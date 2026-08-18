package com.flowforge.api.dto.response;

import com.flowforge.domain.enums.WorkflowStatus;
import com.flowforge.engine.model.Priority;

import java.time.Instant;
import java.util.List;

/**
 * Full workflow representation (with steps). We never expose the JPA entity directly -
 * this decouples the API contract from the persistence model, avoids accidental lazy
 * loading during serialization, and lets the two evolve independently.
 */
public record WorkflowResponse(
        Long id,
        String name,
        String description,
        WorkflowStatus status,
        Priority priority,
        int version,
        Instant createdAt,
        Instant updatedAt,
        List<WorkflowStepResponse> steps
) {
}
