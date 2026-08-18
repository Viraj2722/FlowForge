package com.flowforge.api.mapper;

import com.flowforge.api.dto.request.CreateWorkflowRequest;
import com.flowforge.api.dto.request.CreateWorkflowStepRequest;
import com.flowforge.api.dto.response.ExecutionResponse;
import com.flowforge.api.dto.response.TaskExecutionResponse;
import com.flowforge.api.dto.response.WorkflowResponse;
import com.flowforge.api.dto.response.WorkflowStepResponse;
import com.flowforge.api.dto.response.WorkflowSummaryResponse;
import com.flowforge.domain.entity.TaskExecution;
import com.flowforge.domain.entity.Workflow;
import com.flowforge.domain.entity.WorkflowExecution;
import com.flowforge.domain.entity.WorkflowStep;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between JPA entities and API DTOs, and (de)serialises the JSONB
 * {@code parameters} column to/from a {@code Map}.
 *
 * <p>All methods that touch lazy associations (steps, dependencies, tasks) must be
 * called INSIDE a transaction (the services are {@code @Transactional}); with
 * open-in-view disabled, mapping outside a transaction would throw
 * {@code LazyInitializationException}.
 */
@Component
public class WorkflowMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public WorkflowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ----------------------------- request -> entity -----------------------------

    /**
     * Builds a {@link Workflow} aggregate (with steps and dependency edges) from a
     * create request. Dependencies are declared by {@code stepOrder} and wired here.
     */
    public Workflow toEntity(CreateWorkflowRequest req) {
        Workflow workflow = new Workflow(req.name(), req.description(), req.priority());

        Map<Integer, WorkflowStep> byOrder = new HashMap<>();
        for (CreateWorkflowStepRequest s : req.steps()) {
            WorkflowStep step = new WorkflowStep(s.name(), s.taskType(), s.stepOrder(), writeJson(s.parameters()));
            if (s.maxRetries() != null) {
                step.setMaxRetries(s.maxRetries());
            }
            workflow.addStep(step);
            if (byOrder.putIfAbsent(s.stepOrder(), step) != null) {
                throw new IllegalArgumentException("Duplicate stepOrder " + s.stepOrder());
            }
        }

        // Second pass: now that every step exists, wire the dependency edges.
        for (CreateWorkflowStepRequest s : req.steps()) {
            if (s.dependsOn() == null) {
                continue;
            }
            WorkflowStep step = byOrder.get(s.stepOrder());
            for (Integer predecessorOrder : s.dependsOn()) {
                WorkflowStep predecessor = byOrder.get(predecessorOrder);
                if (predecessor == null) {
                    throw new IllegalArgumentException(
                            "Step " + s.stepOrder() + " depends on unknown stepOrder " + predecessorOrder);
                }
                if (predecessor == step) {
                    throw new IllegalArgumentException("Step " + s.stepOrder() + " cannot depend on itself");
                }
                step.addDependency(predecessor);
            }
        }
        return workflow;
    }

    // ----------------------------- entity -> response ----------------------------

    public WorkflowResponse toResponse(Workflow wf) {
        List<WorkflowStepResponse> steps = wf.getSteps().stream().map(this::toStepResponse).toList();
        return new WorkflowResponse(
                wf.getId(), wf.getName(), wf.getDescription(), wf.getStatus(), wf.getPriority(),
                wf.getVersion(), wf.getCreatedAt(), wf.getUpdatedAt(), steps);
    }

    public WorkflowSummaryResponse toSummary(Workflow wf) {
        return new WorkflowSummaryResponse(
                wf.getId(), wf.getName(), wf.getStatus(), wf.getPriority(), wf.getCreatedAt());
    }

    private WorkflowStepResponse toStepResponse(WorkflowStep step) {
        List<Integer> dependsOn = step.getDependencies().stream()
                .map(WorkflowStep::getStepOrder)
                .sorted()
                .toList();
        return new WorkflowStepResponse(
                step.getId(), step.getName(), step.getTaskType(), step.getStepOrder(),
                readJson(step.getParameters()), step.getMaxRetries(), dependsOn);
    }

    public ExecutionResponse executionToResponse(WorkflowExecution ex) {
        List<TaskExecutionResponse> tasks = ex.getTaskExecutions().stream()
                .map(this::toTaskResponse)
                .toList();
        return new ExecutionResponse(
                ex.getId(), ex.getWorkflow().getId(), ex.getWorkflow().getName(), ex.getStatus(),
                ex.getCorrelationId(), ex.getStartedAt(), ex.getFinishedAt(), ex.getCreatedAt(), tasks);
    }

    private TaskExecutionResponse toTaskResponse(TaskExecution te) {
        WorkflowStep step = te.getWorkflowStep();
        return new TaskExecutionResponse(
                te.getId(), step.getName(), step.getTaskType(), te.getStatus(),
                te.getAttempt(), te.getMaxAttempts(), te.getNextRetryAt(), te.getLastError());
    }

    // ------------------------------- JSON helpers --------------------------------

    private String writeJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid parameters: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException e) {
            // Stored JSON should always be valid; surface loudly if not.
            throw new IllegalStateException("Corrupt stored parameters JSON", e);
        }
    }
}
