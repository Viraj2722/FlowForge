package com.flowforge.engine;

import com.flowforge.engine.model.Priority;
import com.flowforge.engine.model.TaskType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable input passed to a {@link TaskHandler} for one execution attempt.
 *
 * <p>Implemented as a {@code record} (Java 16+) which gives us a final class with
 * {@code equals}/{@code hashCode}/{@code toString} and no boilerplate. Records are a
 * natural fit for value objects.
 *
 * <p><b>Why the defensive copy in the constructor?</b> A record's canonical
 * constructor still stores whatever reference you hand it. If we kept the caller's
 * {@code Map} directly, they could mutate it after construction and break our
 * immutability guarantee. So we copy it and wrap it unmodifiable. This is a classic
 * "defensive copy" — a frequent interview topic (see Effective Java, Item 50).
 *
 * @param taskId        stable identifier of the task instance being executed
 * @param type          which handler should run this task
 * @param priority      business priority (used by rules/scheduling later)
 * @param attempt       1-based attempt number (1 on first try, 2 on first retry, ...)
 * @param parameters    handler-specific inputs (e.g. "to", "url"); never null, never mutable
 * @param correlationId groups all tasks of one workflow execution for tracing/logs
 */
public record TaskContext(
        String taskId,
        TaskType type,
        Priority priority,
        int attempt,
        Map<String, Object> parameters,
        String correlationId
) {

    /**
     * Canonical constructor: validates invariants and defensively copies the map.
     * Runs for every way of building a record, including {@code with}-style rebuilds.
     */
    public TaskContext {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(correlationId, "correlationId");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1 but was " + attempt);
        }
        // Defensive copy + unmodifiable view => callers cannot mutate our state.
        parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(parameters));
    }

    /** Convenience: fetch a parameter without dealing with raw casts everywhere. */
    public Optional<String> stringParam(String key) {
        Object value = parameters.get(key);
        return value == null ? Optional.empty() : Optional.of(value.toString());
    }

    /**
     * Returns a copy of this context representing the next attempt.
     * Used by the retry system so the handler can see which attempt it is on.
     */
    public TaskContext nextAttempt() {
        return new TaskContext(taskId, type, priority, attempt + 1, parameters, correlationId);
    }
}
