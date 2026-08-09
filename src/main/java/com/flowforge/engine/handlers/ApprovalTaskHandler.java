package com.flowforge.engine.handlers;

import com.flowforge.engine.TaskContext;
import com.flowforge.engine.TaskHandler;
import com.flowforge.engine.TaskResult;
import com.flowforge.engine.model.TaskType;

/**
 * Handles {@link TaskType#APPROVAL}: a human decision gate.
 *
 * <p>Unlike the other handlers, an approval is usually <em>not</em> resolved on the
 * first execution — it models a task that waits for an external actor:
 * <ul>
 *   <li>no {@code "decision"} yet -&gt; {@code PENDING} (the engine parks the task and
 *       does not retry or fail it; a later human action supplies the decision).</li>
 *   <li>{@code "decision" = "APPROVED"} -&gt; success.</li>
 *   <li>{@code "decision" = "REJECTED"} -&gt; <b>permanent</b> failure — a rejection is
 *       a terminal business decision, not a transient error, so it must never be
 *       retried.</li>
 * </ul>
 *
 * <p>This shows why {@code PENDING} is a first-class outcome distinct from failure:
 * "waiting on a human" and "the task errored" require completely different handling.
 */
public class ApprovalTaskHandler implements TaskHandler {

    @Override
    public TaskType type() {
        return TaskType.APPROVAL;
    }

    @Override
    public TaskResult handle(TaskContext context) {
        String decision = context.stringParam("decision").orElse(null);
        if (decision == null || decision.isBlank()) {
            return TaskResult.pending("Awaiting approval decision");
        }
        return switch (decision.trim().toUpperCase()) {
            case "APPROVED" -> TaskResult.success("Approved");
            case "REJECTED" -> TaskResult.permanentFailure(
                    "Rejected by approver", new IllegalStateException("approval rejected"));
            default -> TaskResult.permanentFailure(
                    "Unknown decision: " + decision,
                    new IllegalArgumentException("decision must be APPROVED or REJECTED"));
        };
    }
}
