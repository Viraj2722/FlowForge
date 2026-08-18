package com.flowforge.api.dto.response;

import com.flowforge.domain.enums.TaskExecutionStatus;
import com.flowforge.engine.model.TaskType;

import java.time.Instant;

/**
 * One task's execution state within a workflow run.
 */
public record TaskExecutionResponse(
        Long id,
        String stepName,
        TaskType taskType,
        TaskExecutionStatus status,
        int attempt,
        int maxAttempts,
        Instant nextRetryAt,
        String lastError
) {
}
