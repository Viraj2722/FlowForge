package com.flowforge.engine.events;

/**
 * Published after a workflow execution is created (triggered).
 */
public record ExecutionTriggeredEvent(Long executionId, Long workflowId, String correlationId, String actor) {
}
