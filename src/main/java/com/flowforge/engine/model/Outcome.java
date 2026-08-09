package com.flowforge.engine.model;

/**
 * The outcome of executing a single task attempt.
 *
 * <p>We model failure as <em>data</em> (a returned value) rather than only as thrown
 * exceptions, because the engine must make a routing decision from it:
 * <ul>
 *   <li>{@link #SUCCEEDED} — done, no further attempts.</li>
 *   <li>{@link #RETRYABLE_FAILURE} — a transient problem (timeout, 503). The retry
 *       system will schedule another attempt with exponential backoff.</li>
 *   <li>{@link #PERMANENT_FAILURE} — a non-recoverable problem (validation error,
 *       404). Retrying is pointless; the task goes to the dead-letter queue.</li>
 *   <li>{@link #PENDING} — the task is not finished and is waiting on something
 *       external (e.g. a human approval). It is neither a success nor a failure yet.</li>
 * </ul>
 *
 * <p>Distinguishing retryable from permanent failure is the single most important
 * decision the engine makes — retrying a permanent failure just wastes resources and
 * delays the dead-letter signal.
 */
public enum Outcome {
    SUCCEEDED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
    PENDING;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == PERMANENT_FAILURE;
    }

    public boolean isFailure() {
        return this == RETRYABLE_FAILURE || this == PERMANENT_FAILURE;
    }
}
