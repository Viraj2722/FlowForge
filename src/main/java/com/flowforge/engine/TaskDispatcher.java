package com.flowforge.engine;

import com.flowforge.engine.exception.PermanentTaskException;
import com.flowforge.engine.exception.RetryableTaskException;

import java.util.Objects;

/**
 * Executes a single task attempt: looks up the right {@link TaskHandler} and runs it,
 * normalising the two supported failure styles (thrown exceptions vs returned
 * {@link TaskResult}) into a single, always-non-null {@link TaskResult}.
 *
 * <p>This is deliberately <b>synchronous and single-attempt</b>. It knows nothing
 * about threads, retries, scheduling or persistence — those are layered on top in
 * later phases. Keeping this seam tiny and pure is what lets the concurrency layer
 * (Phase 6) and the retry layer (Phase 7) stay independently testable.
 *
 * <p>Thread-safety: holds only an immutable {@link TaskHandlerRegistry}, so a single
 * dispatcher instance is safe to share across threads.
 */
public class TaskDispatcher {

    private final TaskHandlerRegistry registry;

    public TaskDispatcher(TaskHandlerRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Run one attempt of the given task.
     *
     * @param context immutable inputs for this attempt (never null)
     * @return the normalised outcome (never null)
     */
    public TaskResult dispatch(TaskContext context) {
        Objects.requireNonNull(context, "context");
        TaskHandler handler = registry.resolve(context.type());
        try {
            TaskResult result = handler.handle(context);
            if (result == null) {
                // A handler returning null is a bug; fail permanently and loudly.
                return TaskResult.permanentFailure(
                        "Handler " + handler.getClass().getSimpleName() + " returned null",
                        new NullPointerException("handler result was null"));
            }
            return result;
        } catch (RetryableTaskException e) {
            return TaskResult.retryableFailure(e.getMessage(), e);
        } catch (PermanentTaskException e) {
            return TaskResult.permanentFailure(e.getMessage(), e);
        } catch (RuntimeException e) {
            // Unexpected handler bug: default to retryable so a transient glitch does
            // not permanently lose the task; the attempt cap will dead-letter it if
            // the failure is truly persistent.
            return TaskResult.retryableFailure(
                    "Unexpected handler error: " + e.getMessage(), e);
        }
    }
}
