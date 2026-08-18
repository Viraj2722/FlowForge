package com.flowforge.domain.enums;

/**
 * State of a single task execution (one step within one workflow run). Mirrors the
 * CHECK constraint on {@code task_executions.status}, and lines up with the engine's
 * {@link com.flowforge.engine.model.Outcome} plus the retry/dead-letter states the
 * scheduler manages.
 */
public enum TaskExecutionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
    DEAD_LETTER,
    PENDING_APPROVAL,
    CANCELLED
}
