package com.flowforge.service;

import com.flowforge.api.dto.request.CreateWorkflowRequest;
import com.flowforge.api.dto.request.UpdateWorkflowRequest;
import com.flowforge.api.dto.response.WorkflowResponse;
import com.flowforge.api.dto.response.WorkflowSummaryResponse;
import com.flowforge.api.mapper.WorkflowMapper;
import com.flowforge.domain.entity.Workflow;
import com.flowforge.domain.enums.WorkflowStatus;
import com.flowforge.domain.repository.WorkflowRepository;
import com.flowforge.engine.events.WorkflowCreatedEvent;
import com.flowforge.service.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for workflow definitions. Holds the transactional boundaries.
 *
 * <p><b>What {@code @Transactional} does here:</b> Spring wraps each public method in a
 * proxy that opens a transaction (and a Hibernate persistence context) before the method
 * and commits after it returns (rolling back on a runtime exception). Because we map to
 * DTOs <em>inside</em> these methods, all lazy loading happens while the context is open
 * - essential now that open-in-view is disabled.
 *
 * <p>Read methods are {@code readOnly = true}: Hibernate can skip dirty-checking/flush
 * and the driver may optimise the transaction, since we promise not to write.
 */
@Service
@Transactional(readOnly = true)
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowMapper mapper;
    private final ApplicationEventPublisher events;

    public WorkflowService(WorkflowRepository workflowRepository, WorkflowMapper mapper,
                           ApplicationEventPublisher events) {
        this.workflowRepository = workflowRepository;
        this.mapper = mapper;
        this.events = events;
    }

    /**
     * Creates a workflow and its steps in one transaction. If anything fails (e.g. a bad
     * dependency reference, or a DB constraint), the whole thing rolls back - you never
     * get a half-created workflow. This is the "create workflow -> create steps -> create
     * related records, all-or-nothing" boundary.
     */
    @Transactional
    public WorkflowResponse create(CreateWorkflowRequest request) {
        Workflow workflow = mapper.toEntity(request);
        Workflow saved = workflowRepository.save(workflow); // cascade persists steps + deps
        events.publishEvent(new WorkflowCreatedEvent(saved.getId(), saved.getName(), "system"));
        return mapper.toResponse(saved);
    }

    public WorkflowResponse get(Long id) {
        Workflow workflow = workflowRepository.findWithStepsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", id));
        return mapper.toResponse(workflow);
    }

    public Page<WorkflowSummaryResponse> list(WorkflowStatus status, Pageable pageable) {
        Page<Workflow> page = (status == null)
                ? workflowRepository.findAll(pageable)
                : workflowRepository.findByStatus(status, pageable);
        return page.map(mapper::toSummary);
    }

    /**
     * Partial update. We load the managed entity and mutate it; Hibernate's dirty
     * checking detects the changed fields and issues an UPDATE at commit - we don't call
     * save() ourselves. The {@code @Version} column guards against concurrent updates.
     */
    @Transactional
    public WorkflowResponse update(Long id, UpdateWorkflowRequest request) {
        Workflow workflow = workflowRepository.findWithStepsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", id));
        if (request.name() != null) {
            workflow.setName(request.name());
        }
        if (request.description() != null) {
            workflow.setDescription(request.description());
        }
        if (request.priority() != null) {
            workflow.setPriority(request.priority());
        }
        if (request.status() != null) {
            workflow.setStatus(request.status());
        }
        return mapper.toResponse(workflow);
    }

    /**
     * "Delete" = deactivate (soft delete). We keep the row for audit/history and because
     * executions reference it; flipping status to INACTIVE stops it being triggered.
     */
    @Transactional
    public void deactivate(Long id) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", id));
        workflow.setStatus(WorkflowStatus.INACTIVE);
    }
}
