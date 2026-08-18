package com.flowforge.engine.execution;

import com.flowforge.domain.entity.DeadLetterTask;
import com.flowforge.domain.entity.TaskExecution;
import com.flowforge.domain.entity.WorkflowExecution;
import com.flowforge.domain.entity.WorkflowStep;
import com.flowforge.domain.enums.TaskExecutionStatus;
import com.flowforge.domain.repository.DeadLetterTaskRepository;
import com.flowforge.domain.repository.TaskExecutionRepository;
import com.flowforge.engine.TaskContext;
import com.flowforge.engine.TaskDispatcher;
import com.flowforge.engine.TaskResult;
import com.flowforge.engine.events.TaskDeadLetteredEvent;
import com.flowforge.engine.retry.RetryPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Executes ONE task attempt and records the outcome. Each public method is
 * {@code @Transactional}, so when the {@link WorkflowRunner} submits many of these to the
 * worker pool, each runs in its own transaction on its own thread, updating its own row.
 * Because different tasks touch different {@code task_executions} rows, they don't
 * contend; the parent execution row is only touched by the orchestrator.
 *
 * <p>This is the bridge from the framework-free engine ({@link TaskDispatcher} +
 * {@link TaskResult}) to persistent state ({@link TaskExecution} rows and the
 * dead-letter table).
 */
@Service
public class TaskExecutionWorker {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionWorker.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_ERROR_LEN = 4000;

    private final TaskExecutionRepository taskRepository;
    private final DeadLetterTaskRepository deadLetterRepository;
    private final TaskDispatcher dispatcher;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final MeterRegistry meterRegistry;

    public TaskExecutionWorker(TaskExecutionRepository taskRepository,
                               DeadLetterTaskRepository deadLetterRepository,
                               TaskDispatcher dispatcher,
                               ObjectMapper objectMapper,
                               ApplicationEventPublisher events,
                               MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
        this.events = events;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Runs one attempt of the given task and persists the resulting status.
     * Idempotent for terminal tasks: if the task is already finished, it's a no-op.
     */
    @Transactional
    public TaskExecutionStatus execute(Long taskExecutionId) {
        TaskExecution te = taskRepository.findById(taskExecutionId).orElseThrow();

        // Only PENDING (first run) or RETRYABLE_FAILURE (a retry) are runnable.
        if (te.getStatus() != TaskExecutionStatus.PENDING
                && te.getStatus() != TaskExecutionStatus.RETRYABLE_FAILURE) {
            return te.getStatus();
        }

        WorkflowStep step = te.getWorkflowStep();
        WorkflowExecution execution = te.getWorkflowExecution();

        te.setAttempt(te.getAttempt() + 1);
        te.setStatus(TaskExecutionStatus.RUNNING);
        if (te.getStartedAt() == null) {
            te.setStartedAt(Instant.now());
        }

        TaskContext context = new TaskContext(
                String.valueOf(te.getId()),
                step.getTaskType(),
                execution.getWorkflow().getPriority(),
                te.getAttempt(),
                readJson(step.getParameters()),
                execution.getCorrelationId());

        // Put the correlation id in the MDC so every log line from this attempt carries it.
        MDC.put("correlationId", execution.getCorrelationId());
        try {
            // Time the actual handler work and record it per task type.
            Timer.Sample sample = Timer.start(meterRegistry);
            TaskResult result = dispatcher.dispatch(context);
            sample.stop(meterRegistry.timer("flowforge.task.duration", "type", step.getTaskType().name()));

            applyResult(te, step, execution, result);

            // Count completed attempts by their resulting status (SUCCEEDED, DEAD_LETTER, ...).
            meterRegistry.counter("flowforge.tasks.completed", "status", te.getStatus().name()).increment();
            log.debug("task {} attempt {} -> {}", te.getId(), te.getAttempt(), te.getStatus());
            return te.getStatus();
        } finally {
            MDC.remove("correlationId");
        }
    }

    /** Marks a task CANCELLED because a predecessor did not succeed. */
    @Transactional
    public TaskExecutionStatus cancel(Long taskExecutionId, String reason) {
        TaskExecution te = taskRepository.findById(taskExecutionId).orElseThrow();
        if (te.getStatus() == TaskExecutionStatus.PENDING
                || te.getStatus() == TaskExecutionStatus.RETRYABLE_FAILURE) {
            te.setStatus(TaskExecutionStatus.CANCELLED);
            te.setFinishedAt(Instant.now());
            te.setLastError(truncate(reason));
        }
        return te.getStatus();
    }

    private void applyResult(TaskExecution te, WorkflowStep step, WorkflowExecution execution, TaskResult result) {
        Instant now = Instant.now();
        switch (result.outcome()) {
            case SUCCEEDED -> {
                te.setStatus(TaskExecutionStatus.SUCCEEDED);
                te.setFinishedAt(now);
                te.setOutput(writeJson(result.output()));
                te.setLastError(null);
            }
            case RETRYABLE_FAILURE -> {
                te.setLastError(truncate(result.message()));
                if (te.getAttempt() < te.getMaxAttempts()) {
                    // Schedule a retry; the Phase 7 scheduler will pick it up when due.
                    te.setStatus(TaskExecutionStatus.RETRYABLE_FAILURE);
                    Duration backoff = retryPolicyFor(te).delayForAttempt(te.getAttempt());
                    te.setNextRetryAt(now.plus(backoff));
                } else {
                    // Out of attempts -> dead-letter it.
                    te.setStatus(TaskExecutionStatus.DEAD_LETTER);
                    te.setFinishedAt(now);
                    deadLetter(te, step, execution, "retries exhausted: " + result.message());
                }
            }
            case PERMANENT_FAILURE -> {
                te.setStatus(TaskExecutionStatus.PERMANENT_FAILURE);
                te.setFinishedAt(now);
                te.setLastError(truncate(result.message()));
                deadLetter(te, step, execution, "permanent failure: " + result.message());
            }
            case PENDING -> te.setStatus(TaskExecutionStatus.PENDING_APPROVAL);
        }
    }

    private void deadLetter(TaskExecution te, WorkflowStep step, WorkflowExecution execution, String reason) {
        DeadLetterTask dlt = new DeadLetterTask(
                te, execution, step.getTaskType(), te.getAttempt(), truncate(reason));
        dlt.setPayload(step.getParameters());
        deadLetterRepository.save(dlt);
        events.publishEvent(new TaskDeadLetteredEvent(
                te.getId(), execution.getId(), step.getTaskType(), reason));
        log.warn("task {} dead-lettered: {}", te.getId(), reason);
    }

    private RetryPolicy retryPolicyFor(TaskExecution te) {
        // Exponential backoff bounded to 30s, sized to the task's own attempt budget.
        return new RetryPolicy(Math.max(1, te.getMaxAttempts()),
                Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30));
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException e) {
            throw new IllegalStateException("Corrupt step parameters JSON for task", e);
        }
    }

    private String writeJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JacksonException e) {
            return "{}";
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN);
    }
}
