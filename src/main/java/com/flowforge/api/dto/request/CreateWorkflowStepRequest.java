package com.flowforge.api.dto.request;

import com.flowforge.engine.model.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * One step in a create-workflow request.
 *
 * @param name       human-readable step name
 * @param taskType   which engine handler runs this step
 * @param stepOrder  1-based position; also used to reference this step in {@code dependsOn}
 * @param parameters handler inputs (e.g. {@code {"to":"a@b.com"}}); stored as JSONB
 * @param maxRetries optional; defaults to 3 if null
 * @param dependsOn  stepOrder values of steps that must complete before this one (DAG edges)
 */
public record CreateWorkflowStepRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull TaskType taskType,
        @PositiveOrZero int stepOrder,
        Map<String, Object> parameters,
        @PositiveOrZero Integer maxRetries,
        List<Integer> dependsOn
) {
}
