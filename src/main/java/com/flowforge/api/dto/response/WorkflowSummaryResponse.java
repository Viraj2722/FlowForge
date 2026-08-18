package com.flowforge.api.dto.response;

import com.flowforge.domain.enums.WorkflowStatus;
import com.flowforge.engine.model.Priority;

import java.time.Instant;

/**
 * Lightweight workflow view for list endpoints. Deliberately omits steps so listing N
 * workflows does not trigger N lazy step loads (an N+1). Fetch full details via GET by id.
 */
public record WorkflowSummaryResponse(
        Long id,
        String name,
        WorkflowStatus status,
        Priority priority,
        Instant createdAt
) {
}
