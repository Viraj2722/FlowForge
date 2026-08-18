package com.flowforge.api.dto.response;

import com.flowforge.engine.model.TaskType;

import java.time.Instant;

/**
 * A dead-lettered task as shown to operators for inspection / replay.
 */
public record DeadLetterResponse(
        Long id,
        Long taskExecutionId,
        Long workflowExecutionId,
        TaskType taskType,
        int attempts,
        String lastError,
        Instant failedAt,
        boolean replayed
) {
}
