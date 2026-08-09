package com.flowforge.engine;

import com.flowforge.engine.model.TaskType;

/**
 * Strategy interface for executing one kind of task.
 *
 * <p>This is the <b>Strategy pattern</b>. Each concrete handler knows how to do
 * exactly one {@link TaskType}. The engine never contains a
 * {@code switch (type) { case EMAIL: ... }} block — instead it asks the
 * {@link TaskHandlerRegistry} for the handler whose {@link #type()} matches and
 * delegates. This is the Open/Closed Principle in practice: to support a new task
 * type you <em>add</em> a class, you don't <em>modify</em> existing ones.
 *
 * <p><b>Contract for implementors:</b>
 * <ul>
 *   <li>Return a {@link TaskResult} describing the outcome, OR throw
 *       {@link com.flowforge.engine.exception.RetryableTaskException} /
 *       {@link com.flowforge.engine.exception.PermanentTaskException}. The engine
 *       treats both styles equivalently.</li>
 *   <li>Handlers must be <b>stateless and thread-safe</b>: in later phases a single
 *       handler instance is shared across many worker threads executing tasks
 *       concurrently. Keep all per-execution state in local variables / the
 *       {@link TaskContext}, never in fields.</li>
 * </ul>
 */
public interface TaskHandler {

    /** The single task type this handler is responsible for. Must be stable. */
    TaskType type();

    /**
     * Execute one attempt of the task.
     *
     * @param context immutable inputs for this attempt (never null)
     * @return the outcome of this attempt (never null)
     * @throws com.flowforge.engine.exception.RetryableTaskException transient failure
     * @throws com.flowforge.engine.exception.PermanentTaskException  non-recoverable failure
     */
    TaskResult handle(TaskContext context);
}
