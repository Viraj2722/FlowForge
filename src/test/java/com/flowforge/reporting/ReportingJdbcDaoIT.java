package com.flowforge.reporting;

import com.flowforge.reporting.dto.DashboardSummary;
import com.flowforge.reporting.dto.StatusCount;
import com.flowforge.reporting.dto.WorkflowThroughput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link ReportingJdbcDao} against a real PostgreSQL database.
 *
 * <p><b>How it is gated:</b>
 * <ul>
 *   <li>Named {@code *IT}, so the Surefire plugin does NOT run it during {@code mvn
 *       test} (Surefire only picks up {@code *Test}). It won't break the normal build.</li>
 *   <li>{@code @EnabledIfEnvironmentVariable(DB_NAME)} - JUnit skips the whole class
 *       (and therefore never starts a Spring context) unless a database is configured.
 *       So this only runs once you've created the DB and set the env vars.</li>
 *   <li>{@code @Transactional} - every insert this test makes is rolled back at the end,
 *       so it never pollutes your dev database with test data. The schema itself
 *       (created by Flyway) stays.</li>
 * </ul>
 *
 * <p>In Phase 10 this same test is converted to run against a throwaway Testcontainers
 * PostgreSQL, removing the dependency on your local DB entirely.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = ".+")
class ReportingJdbcDaoIT {

    @Autowired
    private ReportingJdbcDao dao;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aggregatesExecutionsStepsFailuresAndDeadLetters() {
        // --- Arrange: build a small, self-contained dataset -------------------
        Long workflowId = jdbc.queryForObject(
                "INSERT INTO workflows (name, status, priority) VALUES (?, ?, ?) RETURNING id",
                Long.class, "Reporting Test WF", "ACTIVE", "HIGH");

        Long stepId = jdbc.queryForObject(
                "INSERT INTO workflow_steps (workflow_id, name, task_type, step_order) "
                        + "VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, workflowId, "call-webhook", "WEBHOOK", 1);

        // Two SUCCEEDED executions (each ~10s long) and one FAILED execution.
        insertExecution(workflowId, "SUCCEEDED", true);
        insertExecution(workflowId, "SUCCEEDED", true);
        Long failedExecId = insertExecution(workflowId, "FAILED", true);

        // A permanently failed task under the failed execution, plus a dead letter.
        Long taskExecId = jdbc.queryForObject(
                "INSERT INTO task_executions "
                        + "(workflow_execution_id, workflow_step_id, status, attempt, max_attempts) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, failedExecId, stepId, "PERMANENT_FAILURE", 4, 4);

        jdbc.update(
                "INSERT INTO dead_letter_tasks "
                        + "(task_execution_id, workflow_execution_id, task_type, attempts, last_error) "
                        + "VALUES (?, ?, ?, ?, ?)",
                taskExecId, failedExecId, "WEBHOOK", 4, "HTTP 400 Bad Request");

        // --- Assert: dashboard summary ---------------------------------------
        DashboardSummary summary = dao.dashboardSummary();
        assertThat(summary.totalWorkflows()).isGreaterThanOrEqualTo(1);
        assertThat(summary.activeWorkflows()).isGreaterThanOrEqualTo(1);
        assertThat(summary.totalExecutions()).isGreaterThanOrEqualTo(3);
        assertThat(summary.deadLetterTasks()).isGreaterThanOrEqualTo(1);

        // --- Assert: counts by status ----------------------------------------
        List<StatusCount> byStatus = dao.executionCountsByStatus();
        assertThat(byStatus)
                .anySatisfy(s -> {
                    assertThat(s.status()).isEqualTo("SUCCEEDED");
                    assertThat(s.count()).isGreaterThanOrEqualTo(2);
                })
                .anySatisfy(s -> {
                    assertThat(s.status()).isEqualTo("FAILED");
                    assertThat(s.count()).isGreaterThanOrEqualTo(1);
                });

        // --- Assert: per-workflow throughput ---------------------------------
        List<WorkflowThroughput> throughput = dao.workflowThroughput(10);
        WorkflowThroughput row = throughput.stream()
                .filter(w -> w.workflowId() == workflowId)
                .findFirst()
                .orElseThrow();
        assertThat(row.totalExecutions()).isEqualTo(3);
        assertThat(row.succeeded()).isEqualTo(2);
        assertThat(row.failed()).isEqualTo(1);
        assertThat(row.avgDurationSeconds()).isNotNull();
        assertThat(row.avgDurationSeconds()).isGreaterThan(0.0);

        // --- Assert: failure breakdown by task type --------------------------
        assertThat(dao.taskFailureBreakdown())
                .anySatisfy(f -> {
                    assertThat(f.taskType()).isEqualTo("WEBHOOK");
                    assertThat(f.failures()).isGreaterThanOrEqualTo(1);
                });
    }

    /** Inserts one workflow_execution; when {@code timed}, gives it a ~10s duration. */
    private Long insertExecution(Long workflowId, String status, boolean timed) {
        String started = timed ? "now() - interval '10 seconds'" : "NULL";
        String finished = timed ? "now()" : "NULL";
        String sql = "INSERT INTO workflow_executions "
                + "(workflow_id, status, correlation_id, started_at, finished_at) "
                + "VALUES (?, ?, ?, " + started + ", " + finished + ") RETURNING id";
        return jdbc.queryForObject(sql, Long.class, workflowId, status, UUID.randomUUID().toString());
    }
}
