package com.flowforge.engine.handlers;

import com.flowforge.engine.TaskContext;
import com.flowforge.engine.TaskHandler;
import com.flowforge.engine.TaskResult;
import com.flowforge.engine.model.TaskType;

/**
 * Handles {@link TaskType#EMAIL}: simulates sending a notification.
 *
 * <p>Behaviour is driven by task parameters so the engine can be tested
 * deterministically:
 * <ul>
 *   <li>missing {@code "to"} -&gt; <b>permanent</b> failure (a validation error can
 *       never be fixed by retrying).</li>
 *   <li>{@code "simulate" = "transient"} -&gt; <b>retryable</b> failure (e.g. SMTP 421).</li>
 *   <li>otherwise -&gt; success.</li>
 * </ul>
 *
 * <p>Stateless: no fields, so one shared instance is safe across worker threads.
 */
public class EmailNotificationTaskHandler implements TaskHandler {

    @Override
    public TaskType type() {
        return TaskType.EMAIL;
    }

    @Override
    public TaskResult handle(TaskContext context) {
        String to = context.stringParam("to").orElse(null);
        if (to == null || to.isBlank()) {
            return TaskResult.permanentFailure(
                    "Missing required 'to' address", new IllegalArgumentException("to is required"));
        }

        String simulate = context.stringParam("simulate").orElse("ok");
        if ("transient".equalsIgnoreCase(simulate)) {
            return TaskResult.retryableFailure(
                    "Mail server temporarily unavailable (simulated)",
                    new RuntimeException("SMTP 421"));
        }

        // Real implementation would call an email provider here.
        return TaskResult.success("Email sent to " + to);
    }
}
