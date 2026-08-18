package com.flowforge.api.dto.response;

import com.flowforge.domain.enums.ExecutionStatus;

import java.time.Instant;
import java.util.List;

/**
 * A workflow execution (one run) with its per-task state.
 */
public record ExecutionResponse(
        Long id,
        Long workflowId,
        String workflowName,
        ExecutionStatus status,
        String correlationId,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        List<TaskExecutionResponse> tasks
) {
}
