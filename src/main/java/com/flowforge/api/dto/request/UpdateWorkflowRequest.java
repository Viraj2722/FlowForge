package com.flowforge.api.dto.request;

import com.flowforge.domain.enums.WorkflowStatus;
import com.flowforge.engine.model.Priority;
import jakarta.validation.constraints.Size;

/**
 * Partial update of a workflow's metadata. All fields optional; null means "leave
 * unchanged". Steps are not edited here (that would be a separate endpoint).
 */
public record UpdateWorkflowRequest(
        @Size(max = 150) String name,
        @Size(max = 1000) String description,
        Priority priority,
        WorkflowStatus status
) {
}
