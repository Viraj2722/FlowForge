package com.flowforge.reporting.dto;

/**
 * Per-workflow execution throughput: how many runs, how many succeeded/failed, and the
 * average wall-clock duration in seconds.
 *
 * <p>This is exactly the kind of result JPA is a poor fit for - it is an aggregation
 * across many rows spanning two tables, returning computed columns that map to no
 * entity. We compute it with a single SQL {@code JOIN ... GROUP BY} and map the flat
 * result straight into this record.
 *
 * @param avgDurationSeconds may be null when a workflow has no finished executions yet
 */
public record WorkflowThroughput(
        long workflowId,
        String workflowName,
        long totalExecutions,
        long succeeded,
        long failed,
        Double avgDurationSeconds
) {
}
