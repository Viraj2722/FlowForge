package com.flowforge.engine.events;

import com.flowforge.engine.model.TaskType;

/**
 * Published when a task is moved to the dead-letter queue.
 */
public record TaskDeadLetteredEvent(Long taskExecutionId, Long executionId, TaskType taskType, String error) {
}
