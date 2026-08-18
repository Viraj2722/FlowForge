package com.flowforge.api.dto.request;

import com.flowforge.api.validation.UniqueStepOrders;
import com.flowforge.engine.model.Priority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload to create a workflow together with its steps in one request.
 *
 * <p>{@code @Valid} on the list makes Bean Validation recurse into each step, so a bad
 * step fails the whole request with a clear field error. Validation runs at the
 * controller boundary before any service/DB work happens.
 */
@UniqueStepOrders
public record CreateWorkflowRequest(
        @NotEmpty @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @NotNull Priority priority,
        @NotEmpty List<@Valid CreateWorkflowStepRequest> steps
) {
}
