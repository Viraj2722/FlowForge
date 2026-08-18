package com.flowforge.api.controller;

import com.flowforge.api.dto.response.ExecutionResponse;
import com.flowforge.domain.enums.ExecutionStatus;
import com.flowforge.engine.execution.ExecutionLauncher;
import com.flowforge.service.ExecutionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST API for triggering and inspecting workflow executions.
 */
@RestController
@RequestMapping("/api/v1")
public class ExecutionController {

    private final ExecutionService executionService;
    private final ExecutionLauncher executionLauncher;

    public ExecutionController(ExecutionService executionService, ExecutionLauncher executionLauncher) {
        this.executionService = executionService;
        this.executionLauncher = executionLauncher;
    }

    /**
     * Triggers a new execution of a workflow. Returns <b>202 Accepted</b>: we have
     * recorded the run and its PENDING tasks, but the actual concurrent processing
     * happens asynchronously (Phases 6-7), so the work is accepted, not yet completed.
     */
    @PostMapping("/workflows/{workflowId}/executions")
    public ResponseEntity<ExecutionResponse> trigger(@PathVariable Long workflowId) {
        // Actor will come from the authenticated principal once security is added (Phase 9).
        ExecutionResponse execution = executionService.trigger(workflowId, "system");
        return ResponseEntity
                .accepted()
                .location(URI.create("/api/v1/executions/" + execution.id()))
                .body(execution);
    }

    /**
     * Starts (or resumes) running an execution asynchronously on the engine. Returns
     * 202 Accepted; poll {@code GET /executions/{id}} to observe progress.
     */
    @PostMapping("/executions/{id}/start")
    public ResponseEntity<ExecutionResponse> start(@PathVariable Long id) {
        ExecutionResponse execution = executionService.get(id); // 404s if it doesn't exist
        executionLauncher.launch(id);
        return ResponseEntity.accepted().body(execution);
    }

    @GetMapping("/executions/{id}")
    public ExecutionResponse get(@PathVariable Long id) {
        return executionService.get(id);
    }

    @GetMapping("/executions")
    public PagedModel<ExecutionResponse> list(
            @RequestParam(required = false) ExecutionStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<ExecutionResponse> page = executionService.list(status, pageable);
        return new PagedModel<>(page);
    }
}
