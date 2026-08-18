package com.flowforge.domain.enums;

/**
 * State of a single workflow <em>execution</em> (one run). Mirrors the CHECK constraint
 * on {@code workflow_executions.status}.
 */
public enum ExecutionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
