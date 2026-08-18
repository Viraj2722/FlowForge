package com.flowforge.api.dto.response;

import com.flowforge.engine.model.TaskType;

import java.util.List;
import java.util.Map;

/**
 * Step as returned to clients. {@code parameters} is parsed back from the stored JSONB
 * into a map; {@code dependsOn} lists the stepOrders of this step's predecessors.
 */
public record WorkflowStepResponse(
        Long id,
        String name,
        TaskType taskType,
        int stepOrder,
        Map<String, Object> parameters,
        int maxRetries,
        List<Integer> dependsOn
) {
}
