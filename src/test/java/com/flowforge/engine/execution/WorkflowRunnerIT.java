package com.flowforge.engine.execution;

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
import com.flowforge.service.ExecutionService;
import com.flowforge.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the concurrent execution engine end-to-end against real PostgreSQL.
 * NOT {@code @Transactional}: the engine runs each task in its own transaction on pool
 * threads, so the test must let those commit and then read the results. We clean up the
 * created workflow (DB FK cascade removes its executions/tasks/dead-letters).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = ".+")
class WorkflowRunnerIT {

    @Autowired private WorkflowService workflowService;
    @Autowired private ExecutionService executionService;
    @Autowired private WorkflowRunner runner;
    @Autowired private JdbcTemplate jdbc;

    private TaskExecutionResponse task(ExecutionResponse ex, String stepName) {
        return ex.tasks().stream().filter(t -> t.stepName().equals(stepName)).findFirst().orElseThrow();
    }

    private long createActivateAndTrigger(CreateWorkflowRequest req) {
        long workflowId = workflowService.create(req).id();
        workflowService.update(workflowId, new UpdateWorkflowRequest(null, null, null, WorkflowStatus.ACTIVE));
        return workflowId;
    }

    @Test
    void parallelStepsSucceedAndDependentRunsAfterThem() {
        CreateWorkflowRequest req = new CreateWorkflowRequest("parallel-ok", null, Priority.HIGH, List.of(
                new CreateWorkflowStepRequest("s1", TaskType.WEBHOOK, 1,
                        Map.of("url", "http://x", "simulateStatus", "200"), null, null),
                new CreateWorkflowStepRequest("s2", TaskType.EMAIL, 2,
                        Map.of("to", "a@b.com"), null, null),
                new CreateWorkflowStepRequest("s3", TaskType.WEBHOOK, 3,
                        Map.of("url", "http://x", "simulateStatus", "200"), null, List.of(1, 2))));
        long workflowId = createActivateAndTrigger(req);
        long execId = executionService.trigger(workflowId, "test").id();
        try {
            ExecutionStatus status = runner.run(execId);
            assertThat(status).isEqualTo(ExecutionStatus.SUCCEEDED);

            ExecutionResponse after = executionService.get(execId);
            assertThat(after.status()).isEqualTo(ExecutionStatus.SUCCEEDED);
            assertThat(after.tasks()).hasSize(3)
                    .allSatisfy(t -> assertThat(t.status()).isEqualTo(TaskExecutionStatus.SUCCEEDED));
        } finally {
            jdbc.update("DELETE FROM workflows WHERE id = ?", workflowId);
        }
    }

    @Test
    void permanentFailureFailsExecutionCancelsDependentAndDeadLetters() {
        long before = jdbc.queryForObject("SELECT count(*) FROM dead_letter_tasks", Long.class);

        CreateWorkflowRequest req = new CreateWorkflowRequest("fail-cancels", null, Priority.MEDIUM, List.of(
                new CreateWorkflowStepRequest("s1", TaskType.WEBHOOK, 1,
                        Map.of("url", "http://x", "simulateStatus", "404"), null, null), // 404 = permanent
                new CreateWorkflowStepRequest("s2", TaskType.EMAIL, 2,
                        Map.of("to", "a@b.com"), null, List.of(1))));
        long workflowId = createActivateAndTrigger(req);
        long execId = executionService.trigger(workflowId, "test").id();
        try {
            ExecutionStatus status = runner.run(execId);
            assertThat(status).isEqualTo(ExecutionStatus.FAILED);

            ExecutionResponse after = executionService.get(execId);
            assertThat(task(after, "s1").status()).isEqualTo(TaskExecutionStatus.PERMANENT_FAILURE);
            assertThat(task(after, "s2").status()).isEqualTo(TaskExecutionStatus.CANCELLED);

            long dlAfter = jdbc.queryForObject("SELECT count(*) FROM dead_letter_tasks", Long.class);
            assertThat(dlAfter).isEqualTo(before + 1);
        } finally {
            jdbc.update("DELETE FROM workflows WHERE id = ?", workflowId);
        }
    }

    @Test
    void retryableFailureSchedulesARetryAndLeavesExecutionRunning() {
        CreateWorkflowRequest req = new CreateWorkflowRequest("retry-sched", null, Priority.LOW, List.of(
                new CreateWorkflowStepRequest("flaky", TaskType.EMAIL, 1,
                        Map.of("to", "a@b.com", "simulate", "transient"), 2, null))); // maxRetries=2
        long workflowId = createActivateAndTrigger(req);
        long execId = executionService.trigger(workflowId, "test").id();
        try {
            ExecutionStatus status = runner.run(execId);
            assertThat(status).isEqualTo(ExecutionStatus.RUNNING); // still waiting on a retry

            TaskExecutionResponse flaky = task(executionService.get(execId), "flaky");
            assertThat(flaky.status()).isEqualTo(TaskExecutionStatus.RETRYABLE_FAILURE);
            assertThat(flaky.attempt()).isEqualTo(1);
            assertThat(flaky.nextRetryAt()).isNotNull();
        } finally {
            jdbc.update("DELETE FROM workflows WHERE id = ?", workflowId);
        }
    }
}
