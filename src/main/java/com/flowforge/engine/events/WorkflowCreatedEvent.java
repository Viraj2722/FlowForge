package com.flowforge.engine.events;

/**
 * Published after a workflow is created. Consumed by the audit listener.
 * Events are plain immutable records - no Spring base class needed since Spring 4.2.
 */
public record WorkflowCreatedEvent(Long workflowId, String name, String actor) {
}
