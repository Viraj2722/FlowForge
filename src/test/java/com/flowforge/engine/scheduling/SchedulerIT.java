package com.flowforge.engine.scheduling;

import com.flowforge.api.dto.request.CreateWorkflowRequest;
import com.flowforge.api.dto.request.CreateWorkflowStepRequest;
import com.flowforge.api.dto.request.UpdateWorkflowRequest;
import com.flowforge.api.dto.response.ExecutionResponse;
import com.flowforge.api.dto.response.TaskExecutionResponse;
import com.flowforge.domain.enums.ExecutionStatus;
import com.flowforge.domain.enums.TaskExecutionStatus;
import com.flowforge.domain.enums.WorkflowStatus;
import com.flowforge.engine.model.Priority;
import com.flowforge.engine.model.TaskType;
import com.flowforge.engine.execution.WorkflowRunner;
import com.flowforge.service.DeadLetterService;
import com.flowforge.service.ExecutionService;
import com.flowforge.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Verifies the scheduler + dead-letter replay. The background timers are disabled in
 * tests (application.properties), so we invoke the poll methods directly for determinism.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = ".+")
class SchedulerIT {

    @Autowired private WorkflowService workflowService;
    @Autowired private ExecutionService executionService;
    @Autowired private DeadLetterService deadLetterService;
    @Autowired private ExecutionScheduler scheduler;
    @Autowired private WorkflowRunner runner;
    @Autowired private JdbcTemplate jdbc;

    private long createActiveWorkflow(String name, CreateWorkflowStepRequest... steps) {
        long id = workflowService.create(
                new CreateWorkflowRequest(name, null, Priority.MEDIUM, List.of(steps))).id();
        workflowService.update(id, new UpdateWorkflowRequest(null, null, null, WorkflowStatus.ACTIVE));
        return id;
    }

    private TaskExecutionResponse task(long execId, String stepName) {
        return executionService.get(execId).tasks().stream()
                .filter(t -> t.stepName().equals(stepName)).findFirst().orElseThrow();
    }

    private void await(BooleanSupplier condition, Duration timeout) {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        fail("condition not satisfied within " + timeout);
    }

    @Test
    void pollPendingExecutionsStartsAndRunsThem() {
        long wfId = createActiveWorkflow("sched-pending",
                new CreateWorkflowStepRequest("s1", TaskType.WEBHOOK, 1,
                        Map.of("url", "http://x", "simulateStatus", "200"), null, null));
        long execId = executionService.trigger(wfId, "test").id();
        try {
            assertThat(executionService.get(execId).status()).isEqualTo(ExecutionStatus.PENDING);

            scheduler.pollPendingExecutions(); // launches asynchronously

            await(() -> executionService.get(execId).status() == ExecutionStatus.SUCCEEDED,
                    Duration.ofSeconds(15));
        } finally {
            jdbc.update("DELETE FROM workflows WHERE id = ?", wfId);
        }
    }

    @Test
    void pollDueRetriesRelaunchesAndRetriesTheTask() {
        long wfId = createActiveWorkflow("sched-retry",
                new CreateWorkflowStepRequest("flaky", TaskType.EMAIL, 1,
                        Map.of("to", "a@b.com", "simulate", "transient"), 3, null)); // maxRetries=3
        long execId = executionService.trigger(wfId, "test").id();
        try {
            runner.run(execId); // first attempt -> RETRYABLE_FAILURE, attempt 1
            assertThat(task(execId, "flaky").status()).isEqualTo(TaskExecutionStatus.RETRYABLE_FAILURE);
            assertThat(task(execId, "flaky").attempt()).isEqualTo(1);

            // Make the retry due right now, then let the scheduler pick it up.
            jdbc.update("UPDATE task_executions SET next_retry_at = now() - interval '1 second' "
                    + "WHERE workflow_execution_id = ?", execId);

            scheduler.pollDueRetries();

            await(() -> task(execId, "flaky").attempt() >= 2, Duration.ofSeconds(15));
        } finally {
            jdbc.update("DELETE FROM workflows WHERE id = ?", wfId);
        }
    }

    @Test
    void deadLetterReplayResetsTaskAndExecution() {
        long wfId = createActiveWorkflow("sched-dlq",
                new CreateWorkflowStepRequest("bad", TaskType.WEBHOOK, 1,
                        Map.of("url", "http://x", "simulateStatus", "404"), null, null)); // permanent
        long execId = executionService.trigger(wfId, "test").id();
        try {
            runner.run(execId);
            assertThat(task(execId, "bad").status()).isEqualTo(TaskExecutionStatus.PERMANENT_FAILURE);

            Long dltId = jdbc.queryForObject(
                    "SELECT id FROM dead_letter_tasks WHERE workflow_execution_id = ?", Long.class, execId);

            Long returnedExecId = deadLetterService.replay(dltId);
            assertThat(returnedExecId).isEqualTo(execId);

            // Task and execution reset to PENDING; dead letter marked replayed.
            assertThat(task(execId, "bad").status()).isEqualTo(TaskExecutionStatus.PENDING);
            assertThat(executionService.get(execId).status()).isEqualTo(ExecutionStatus.PENDING);
            boolean replayed = jdbc.queryForObject(
                    "SELECT replayed FROM dead_letter_tasks WHERE id = ?", Boolean.class, dltId);
            assertThat(replayed).isTrue();
        } finally {
            jdbc.update("DELETE FROM workflows WHERE id = ?", wfId);
        }
    }
}
