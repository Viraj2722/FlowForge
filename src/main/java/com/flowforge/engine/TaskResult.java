package com.flowforge.engine;

import com.flowforge.engine.model.Outcome;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of one task execution attempt.
 *
 * <p>A handler returns a {@code TaskResult} instead of just throwing, so the engine
 * can inspect the {@link Outcome} and decide what to do next (finish / retry /
 * dead-letter / wait). The static factory methods below are the intended way to
 * build one — they read clearly at the call site: {@code TaskResult.success(...)}.
 *
 * @param outcome  what happened (never null)
 * @param message  short human-readable summary for logs/audit (never null)
 * @param output   data produced by the task, e.g. an HTTP status; never null/mutable
 * @param error    the throwable that caused a failure, if any (may be null)
 */
public record TaskResult(
        Outcome outcome,
        String message,
        Map<String, Object> output,
        Throwable error
) {

    public TaskResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(message, "message");
        output = output == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(output));
    }

    // --- Factory methods: the readable, intent-revealing way to construct results ---

    public static TaskResult success(String message) {
        return new TaskResult(Outcome.SUCCEEDED, message, Map.of(), null);
    }

    public static TaskResult success(String message, Map<String, Object> output) {
        return new TaskResult(Outcome.SUCCEEDED, message, output, null);
    }

    /** A transient failure — the engine should retry with backoff. */
    public static TaskResult retryableFailure(String message, Throwable cause) {
        return new TaskResult(Outcome.RETRYABLE_FAILURE, message, Map.of(), cause);
    }

    /** A non-recoverable failure — the engine should dead-letter, not retry. */
    public static TaskResult permanentFailure(String message, Throwable cause) {
        return new TaskResult(Outcome.PERMANENT_FAILURE, message, Map.of(), cause);
    }

    /** The task is waiting on something external (e.g. a human approval). */
    public static TaskResult pending(String message) {
        return new TaskResult(Outcome.PENDING, message, Map.of(), null);
    }

    public Optional<Throwable> errorOptional() {
        return Optional.ofNullable(error);
    }
}
