package com.flowforge.engine;

import com.flowforge.engine.model.TaskType;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the {@link TaskHandler} responsible for a given {@link TaskType}.
 *
 * <p>This is the counterpart to the Strategy pattern: a small registry/factory that
 * turns a {@code TaskType} into the right strategy in O(1), replacing what would
 * otherwise be a sprawling {@code switch}/{@code if-else} chain scattered through the
 * engine.
 *
 * <p><b>Design choices worth defending in an interview:</b>
 * <ul>
 *   <li><b>{@link EnumMap}</b> — the most efficient {@code Map} when keys are enum
 *       constants; internally it is just an array indexed by {@code ordinal()}.</li>
 *   <li><b>Built once, then read-only</b> — the map is fully populated in the
 *       constructor and never mutated again, which makes the registry <b>immutable
 *       and therefore thread-safe</b> with no locking. Safe to share across all
 *       worker threads.</li>
 *   <li><b>Fail fast</b> — registering two handlers for the same type throws
 *       immediately (a programming error we want to catch at startup, not in prod).</li>
 * </ul>
 *
 * <p>In later phases Spring will collect all {@code TaskHandler} beans and inject
 * them here automatically. For now it is a plain constructor — no framework needed.
 */
public class TaskHandlerRegistry {

    private final Map<TaskType, TaskHandler> handlers;

    /**
     * @param handlers all available handlers; exactly one per {@link TaskType} it
     *                 claims. Duplicate types are rejected.
     * @throws IllegalArgumentException if two handlers claim the same type
     * @throws NullPointerException     if the collection or any handler/type is null
     */
    public TaskHandlerRegistry(Collection<TaskHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers");
        Map<TaskType, TaskHandler> map = new EnumMap<>(TaskType.class);
        for (TaskHandler handler : handlers) {
            Objects.requireNonNull(handler, "handler");
            TaskType type = Objects.requireNonNull(handler.type(), "handler.type()");
            TaskHandler previous = map.putIfAbsent(type, handler);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate handler for task type " + type + ": "
                                + previous.getClass().getName() + " and "
                                + handler.getClass().getName());
            }
        }
        this.handlers = map; // never mutated after this point
    }

    /**
     * @return the handler for {@code type}
     * @throws IllegalArgumentException if no handler is registered for the type
     */
    public TaskHandler resolve(TaskType type) {
        Objects.requireNonNull(type, "type");
        TaskHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for task type " + type);
        }
        return handler;
    }

    public boolean supports(TaskType type) {
        return handlers.containsKey(type);
    }

    public int size() {
        return handlers.size();
    }
}
