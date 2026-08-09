package com.flowforge.reporting.dto;

/**
 * Top-level counters for a dashboard landing page. A single flat snapshot of the
 * system's current state, assembled from several cheap COUNT queries.
 */
public record DashboardSummary(
        long totalWorkflows,
        long activeWorkflows,
        long totalExecutions,
        long runningExecutions,
        long deadLetterTasks
) {
}
