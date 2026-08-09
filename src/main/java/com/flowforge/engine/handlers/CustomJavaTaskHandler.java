package com.flowforge.engine.handlers;

import com.flowforge.engine.TaskContext;
import com.flowforge.engine.TaskHandler;
import com.flowforge.engine.TaskResult;
import com.flowforge.engine.exception.PermanentTaskException;
import com.flowforge.engine.exception.RetryableTaskException;
import com.flowforge.engine.model.TaskType;

/**
 * Handles {@link TaskType#CUSTOM}: runs an arbitrary {@link CustomTaskAction} supplied
 * in the task parameters under {@link #ACTION_PARAM}.
 *
 * <p>This is the "extension point" of the engine — it lets application code plug in
 * bespoke logic without inventing a new {@link TaskType}. It also demonstrates
 * disciplined exception translation:
 * <ul>
 *   <li>the action may throw {@link RetryableTaskException} or
 *       {@link PermanentTaskException} to control routing explicitly;</li>
 *   <li>any other exception is treated as <b>retryable by default</b> — an unexpected
 *       error is more safely retried than silently dropped, and if it keeps failing the
 *       {@link com.flowforge.engine.retry.RetryPolicy}'s attempt cap will eventually
 *       dead-letter it anyway.</li>
 * </ul>
 *
 * <p>Stateless: the per-task action lives in the {@link TaskContext}, not in a field,
 * so the shared handler instance stays thread-safe.
 */
public class CustomJavaTaskHandler implements TaskHandler {

    /** Parameter key under which a {@link CustomTaskAction} must be supplied. */
    public static final String ACTION_PARAM = "action";

    @Override
    public TaskType type() {
        return TaskType.CUSTOM;
    }

    @Override
    public TaskResult handle(TaskContext context) {
        Object raw = context.parameters().get(ACTION_PARAM);
        if (!(raw instanceof CustomTaskAction action)) {
            return TaskResult.permanentFailure(
                    "CUSTOM task requires a CustomTaskAction under '" + ACTION_PARAM + "'",
                    new IllegalArgumentException("missing or wrong-typed action"));
        }

        try {
            TaskResult result = action.run(context);
            if (result == null) {
                return TaskResult.permanentFailure(
                        "Custom action returned null",
                        new NullPointerException("action result was null"));
            }
            return result;
        } catch (RetryableTaskException e) {
            return TaskResult.retryableFailure(e.getMessage(), e);
        } catch (PermanentTaskException e) {
            return TaskResult.permanentFailure(e.getMessage(), e);
        } catch (Exception e) {
            // Unknown failure: default to retryable (safer than dropping the task).
            return TaskResult.retryableFailure("Custom action failed: " + e.getMessage(), e);
        }
    }
}
