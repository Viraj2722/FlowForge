package com.flowforge.engine.exception;

/**
 * Thrown by a handler to signal a <em>non-recoverable</em> failure — retrying would
 * never succeed (validation error, HTTP 404/400, malformed input, ...).
 *
 * <p>The engine maps this to
 * {@link com.flowforge.engine.model.Outcome#PERMANENT_FAILURE} and routes the task
 * straight to the dead-letter queue instead of scheduling a retry. Getting this
 * distinction right is what stops the system from hammering a doomed request.
 */
public class PermanentTaskException extends RuntimeException {

    public PermanentTaskException(String message) {
        super(message);
    }

    public PermanentTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
