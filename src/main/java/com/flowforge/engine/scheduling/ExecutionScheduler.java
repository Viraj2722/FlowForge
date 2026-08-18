package com.flowforge.engine.scheduling;

import com.flowforge.domain.enums.ExecutionStatus;
import com.flowforge.domain.enums.TaskExecutionStatus;
import com.flowforge.domain.repository.TaskExecutionRepository;
import com.flowforge.domain.repository.WorkflowExecutionRepository;
import com.flowforge.engine.execution.ExecutionLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Background pollers that move work forward without anyone calling the API.
 *
 * <p>Two responsibilities, each on its own fixed-delay timer:
 * <ul>
 *   <li><b>Pending executions</b> - executions created (e.g. by the trigger endpoint) but
 *       not yet started are picked up and launched.</li>
 *   <li><b>Due retries</b> - executions with a retryable task whose {@code next_retry_at}
 *       has arrived are relaunched, so the failed task is retried with the backoff that
 *       Phase 6 scheduled.</li>
 * </ul>
 *
 * <p>{@code fixedDelay} (not {@code fixedRate}) means the next run starts only after the
 * previous finishes, so a slow poll can never overlap itself. Bounded batch sizes keep a
 * single tick from pulling an unbounded backlog. The {@link ExecutionLauncher}'s in-flight
 * guard makes relaunching an already-running execution a harmless no-op.
 *
 * <p>Single-instance assumption: these pollers assume one running app. In a multi-instance
 * deployment you'd add a shared lock (e.g. ShedLock) or {@code SELECT ... FOR UPDATE SKIP
 * LOCKED} so two instances don't grab the same work - a known limitation, documented.
 */
@Component
public class ExecutionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExecutionScheduler.class);

    private final WorkflowExecutionRepository executionRepository;
    private final TaskExecutionRepository taskRepository;
    private final ExecutionLauncher launcher;
    private final int batchSize;

    public ExecutionScheduler(WorkflowExecutionRepository executionRepository,
                              TaskExecutionRepository taskRepository,
                              ExecutionLauncher launcher,
                              @Value("${flowforge.scheduler.batch-size:50}") int batchSize) {
        this.executionRepository = executionRepository;
        this.taskRepository = taskRepository;
        this.launcher = launcher;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${flowforge.scheduler.pending-interval-ms:5000}",
            initialDelayString = "${flowforge.scheduler.initial-delay-ms:5000}")
    public void pollPendingExecutions() {
        List<Long> ids = executionRepository.findIdsByStatus(
                ExecutionStatus.PENDING, PageRequest.of(0, batchSize));
        if (!ids.isEmpty()) {
            log.debug("Scheduler starting {} pending execution(s)", ids.size());
            ids.forEach(launcher::launch);
        }
    }

    @Scheduled(fixedDelayString = "${flowforge.scheduler.retry-interval-ms:5000}",
            initialDelayString = "${flowforge.scheduler.initial-delay-ms:5000}")
    public void pollDueRetries() {
        List<Long> ids = taskRepository.findExecutionIdsWithDueRetries(
                TaskExecutionStatus.RETRYABLE_FAILURE, Instant.now(), PageRequest.of(0, batchSize));
        if (!ids.isEmpty()) {
            log.debug("Scheduler relaunching {} execution(s) with due retries", ids.size());
            ids.forEach(launcher::launch);
        }
    }
}
