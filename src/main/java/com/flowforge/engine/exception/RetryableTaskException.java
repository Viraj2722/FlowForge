package com.flowforge.engine.exception;

/**
 * Thrown by a handler to signal a <em>transient</em> failure that is worth retrying
 * (network timeout, HTTP 503, temporary lock contention, ...).
 *
 * <p>This is an unchecked exception ({@code extends RuntimeException}). We prefer
 * unchecked here because task handlers are plugged in via a functional-style API and
 * checked exceptions would force {@code throws} clauses to leak through the whole
 * engine. The engine's executor catches this and maps it to
 * {@link com.flowforge.engine.model.Outcome#RETRYABLE_FAILURE}.
 *
 * <p>A handler may either throw this or return
 * {@link com.flowforge.engine.TaskResult#retryableFailure} — both are honoured.
 */
public class RetryableTaskException extends RuntimeException {

    public RetryableTaskException(String message) {
        super(message);
    }

    public RetryableTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
