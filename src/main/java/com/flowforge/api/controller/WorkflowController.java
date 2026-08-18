package com.flowforge.api.controller;

import com.flowforge.api.dto.request.CreateWorkflowRequest;
import com.flowforge.api.dto.request.UpdateWorkflowRequest;
import com.flowforge.api.dto.response.WorkflowResponse;
import com.flowforge.api.dto.response.WorkflowSummaryResponse;
import com.flowforge.domain.enums.WorkflowStatus;
import com.flowforge.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST API for workflow definitions.
 *
 * <p>Conventions on display here (all interview-relevant):
 * <ul>
 *   <li>Versioned base path {@code /api/v1} so the contract can evolve.</li>
 *   <li>Correct verbs + status codes: POST-&gt;201 with a {@code Location} header,
 *       PUT-&gt;200, DELETE-&gt;204.</li>
 *   <li>Never accept or return JPA entities - only DTOs cross the boundary.</li>
 *   <li>Listing is paginated/sortable/filterable via Spring Data's {@link Pageable}
 *       ({@code ?page=0&size=20&sort=name,asc&status=ACTIVE}) and returned as a stable
 *       {@link PagedModel} envelope.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    public ResponseEntity<WorkflowResponse> create(@Valid @RequestBody CreateWorkflowRequest request) {
        WorkflowResponse created = workflowService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/workflows/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    public WorkflowResponse get(@PathVariable Long id) {
        return workflowService.get(id);
    }

    @GetMapping
    public PagedModel<WorkflowSummaryResponse> list(
            @RequestParam(required = false) WorkflowStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<WorkflowSummaryResponse> page = workflowService.list(status, pageable);
        return new PagedModel<>(page);
    }

    @PutMapping("/{id}")
    public WorkflowResponse update(@PathVariable Long id,
                                   @Valid @RequestBody UpdateWorkflowRequest request) {
        return workflowService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        workflowService.deactivate(id);
    }
}
