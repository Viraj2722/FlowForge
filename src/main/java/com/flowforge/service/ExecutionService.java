package com.flowforge.service;

import com.flowforge.api.dto.response.ExecutionResponse;
import com.flowforge.api.mapper.WorkflowMapper;
import com.flowforge.domain.entity.TaskExecution;
import com.flowforge.domain.entity.Workflow;
import com.flowforge.domain.entity.WorkflowExecution;
import com.flowforge.domain.entity.WorkflowStep;
import com.flowforge.domain.enums.ExecutionStatus;
import com.flowforge.domain.enums.WorkflowStatus;
import com.flowforge.domain.repository.WorkflowExecutionRepository;
import com.flowforge.domain.repository.WorkflowRepository;
import com.flowforge.engine.events.ExecutionTriggeredEvent;
import com.flowforge.service.exception.ResourceNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for triggering and inspecting workflow executions.
 *
 * <p>In this phase, "trigger" only <em>creates</em> the execution record and one
 * {@link TaskExecution} row per step, all in PENDING state. Actually <em>running</em>
 * them concurrently (with retries and dead-lettering) is Phases 6-7. Separating
 * "record the intent to run" from "run it" keeps the API fast and the execution
 * asynchronous.
 */
@Service
@Transactional(readOnly = true)
public class ExecutionService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowMapper mapper;
    private final ApplicationEventPublisher events;
    private final MeterRegistry meterRegistry;

    public ExecutionService(WorkflowRepository workflowRepository,
                            WorkflowExecutionRepository executionRepository,
                            WorkflowMapper mapper,
                            ApplicationEventPublisher events,
                            MeterRegistry meterRegistry) {
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.mapper = mapper;
        this.events = events;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Creates a new execution for an ACTIVE workflow: one PENDING task per step, in a
     * single transaction (cascade persist). Rejects non-ACTIVE workflows.
     */
    @Transactional
    public ExecutionResponse trigger(Long workflowId, String actor) {
        Workflow workflow = workflowRepository.findWithStepsById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", workflowId));

        if (workflow.getStatus() != WorkflowStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Workflow must be ACTIVE to trigger, but was " + workflow.getStatus());
        }

        WorkflowExecution execution = new WorkflowExecution(workflow, UUID.randomUUID().toString());
        execution.setStatus(ExecutionStatus.PENDING);
        for (WorkflowStep step : workflow.getSteps()) {
            // max attempts = the initial try + the configured retries
            TaskExecution task = new TaskExecution(step, step.getMaxRetries() + 1);
            execution.addTaskExecution(task);
        }

        WorkflowExecution saved = executionRepository.save(execution); // cascades tasks
        events.publishEvent(new ExecutionTriggeredEvent(
                saved.getId(), workflow.getId(), saved.getCorrelationId(), actor));
        meterRegistry.counter("flowforge.executions.triggered").increment();
        return mapper.executionToResponse(saved);
    }

    public ExecutionResponse get(Long id) {
        WorkflowExecution execution = executionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Execution", id));
        return mapper.executionToResponse(execution);
    }

    public Page<ExecutionResponse> list(ExecutionStatus status, Pageable pageable) {
        Page<WorkflowExecution> page = (status == null)
                ? executionRepository.findAll(pageable)
                : executionRepository.findByStatus(status, pageable);
        return page.map(mapper::executionToResponse);
    }
}
