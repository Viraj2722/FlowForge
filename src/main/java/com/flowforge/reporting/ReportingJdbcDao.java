package com.flowforge.reporting;

import com.flowforge.reporting.dto.DashboardSummary;
import com.flowforge.reporting.dto.StatusCount;
import com.flowforge.reporting.dto.TaskTypeFailureCount;
import com.flowforge.reporting.dto.WorkflowThroughput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only reporting/analytics queries implemented with <b>plain JDBC</b>
 * (Spring's {@link JdbcTemplate}), on purpose.
 *
 * <h2>Why JDBC here instead of JPA?</h2>
 * These are aggregate/projection queries: {@code GROUP BY}, {@code JOIN}, computed
 * columns like average duration. Doing them through JPA would be the wrong tool:
 * <ul>
 *   <li>There is no entity to hydrate - the results are flat report rows, so loading
 *       managed entities into the persistence context would waste memory and add
 *       dirty-checking overhead we don't want on a read path.</li>
 *   <li>The SQL uses PostgreSQL features (aggregate {@code FILTER}, {@code EXTRACT
 *       (EPOCH ...)}) that are clearest written as SQL, not coaxed out of JPQL.</li>
 *   <li>We want the <b>database</b> to do the aggregation and hand back a small result,
 *       rather than pulling rows into the app to aggregate in Java.</li>
 * </ul>
 * The transactional domain writes (create workflow + steps, update execution state)
 * go through JPA in later phases, where identity, cascades and optimistic locking earn
 * their keep. This split - ORM for writes, SQL for reporting reads - is a common,
 * defensible production pattern (a lightweight flavour of CQRS).
 *
 * <h2>What {@link JdbcTemplate} does for us</h2>
 * It borrows a pooled {@link java.sql.Connection}, creates a
 * {@link java.sql.PreparedStatement} (so parameters are bound safely - no SQL
 * injection), executes it, maps each {@link java.sql.ResultSet} row via a
 * {@link RowMapper}, and - crucially - always closes the ResultSet, Statement and
 * Connection even on error. It also translates {@code SQLException} into Spring's
 * unchecked {@code DataAccessException} hierarchy. That is exactly the boilerplate you
 * would otherwise write by hand with try-with-resources.
 *
 * <p>Thread-safe: {@link JdbcTemplate} is thread-safe once configured, and this class
 * holds no mutable state, so a single instance is safely shared across request threads.
 */
@Repository
public class ReportingJdbcDao {

    private final JdbcTemplate jdbc;

    public ReportingJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Count of workflow executions grouped by status (PENDING/RUNNING/SUCCEEDED/...). */
    public List<StatusCount> executionCountsByStatus() {
        String sql = """
                SELECT status, count(*) AS cnt
                FROM workflow_executions
                GROUP BY status
                ORDER BY status
                """;
        RowMapper<StatusCount> mapper = (rs, rowNum) ->
                new StatusCount(rs.getString("status"), rs.getLong("cnt"));
        return jdbc.query(sql, mapper);
    }

    /**
     * Per-workflow throughput, most-run first, limited to {@code limit} rows.
     *
     * <p>Uses PostgreSQL's aggregate {@code FILTER (WHERE ...)} to count succeeded and
     * failed in the same pass, and a LEFT JOIN so workflows with zero executions still
     * appear (with counts of 0 and a null average).
     *
     * @param limit maximum rows to return; must be positive
     */
    public List<WorkflowThroughput> workflowThroughput(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0 but was " + limit);
        }
        String sql = """
                SELECT w.id                                                              AS workflow_id,
                       w.name                                                            AS workflow_name,
                       count(e.id)                                                       AS total_executions,
                       count(*) FILTER (WHERE e.status = 'SUCCEEDED')                    AS succeeded,
                       count(*) FILTER (WHERE e.status = 'FAILED')                       AS failed,
                       avg(EXTRACT(EPOCH FROM (e.finished_at - e.started_at)))
                           FILTER (WHERE e.finished_at IS NOT NULL)                      AS avg_duration_seconds
                FROM workflows w
                LEFT JOIN workflow_executions e ON e.workflow_id = w.id
                GROUP BY w.id, w.name
                ORDER BY total_executions DESC, w.id
                LIMIT ?
                """;
        RowMapper<WorkflowThroughput> mapper = (rs, rowNum) -> new WorkflowThroughput(
                rs.getLong("workflow_id"),
                rs.getString("workflow_name"),
                rs.getLong("total_executions"),
                rs.getLong("succeeded"),
                rs.getLong("failed"),
                // getObject(..., Double.class) preserves SQL NULL as Java null,
                // unlike getDouble() which would silently return 0.0.
                rs.getObject("avg_duration_seconds", Double.class));
        return jdbc.query(sql, mapper, limit);
    }

    /** Failed task executions grouped by the step's task type. */
    public List<TaskTypeFailureCount> taskFailureBreakdown() {
        String sql = """
                SELECT ws.task_type              AS task_type,
                       count(*)                  AS failures
                FROM task_executions te
                JOIN workflow_steps ws ON ws.id = te.workflow_step_id
                WHERE te.status IN ('PERMANENT_FAILURE', 'DEAD_LETTER')
                GROUP BY ws.task_type
                ORDER BY failures DESC
                """;
        RowMapper<TaskTypeFailureCount> mapper = (rs, rowNum) ->
                new TaskTypeFailureCount(rs.getString("task_type"), rs.getLong("failures"));
        return jdbc.query(sql, mapper);
    }

    /**
     * Single-round-trip dashboard summary. All counters are computed with scalar
     * subqueries in one statement, which is cheaper than five separate calls.
     */
    public DashboardSummary dashboardSummary() {
        String sql = """
                SELECT
                    (SELECT count(*) FROM workflows)                                   AS total_workflows,
                    (SELECT count(*) FROM workflows WHERE status = 'ACTIVE')           AS active_workflows,
                    (SELECT count(*) FROM workflow_executions)                         AS total_executions,
                    (SELECT count(*) FROM workflow_executions WHERE status = 'RUNNING') AS running_executions,
                    (SELECT count(*) FROM dead_letter_tasks WHERE replayed = FALSE)    AS dead_letter_tasks
                """;
        RowMapper<DashboardSummary> mapper = (rs, rowNum) -> new DashboardSummary(
                rs.getLong("total_workflows"),
                rs.getLong("active_workflows"),
                rs.getLong("total_executions"),
                rs.getLong("running_executions"),
                rs.getLong("dead_letter_tasks"));
        // queryForObject expects exactly one row; this query always returns one.
        return jdbc.queryForObject(sql, mapper);
    }
}
