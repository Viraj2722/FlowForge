package com.flowforge.api.controller;

import com.flowforge.api.dto.response.DeadLetterResponse;
import com.flowforge.api.dto.response.ExecutionResponse;
import com.flowforge.engine.execution.ExecutionLauncher;
import com.flowforge.service.DeadLetterService;
import com.flowforge.service.ExecutionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dead-letter queue API: inspect failed tasks and replay them.
 */
@RestController
@RequestMapping("/api/v1/dead-letters")
public class DeadLetterController {

    private final DeadLetterService deadLetterService;
    private final ExecutionService executionService;
    private final ExecutionLauncher executionLauncher;

    public DeadLetterController(DeadLetterService deadLetterService,
                                ExecutionService executionService,
                                ExecutionLauncher executionLauncher) {
        this.deadLetterService = deadLetterService;
        this.executionService = executionService;
        this.executionLauncher = executionLauncher;
    }

    @GetMapping
    public PagedModel<DeadLetterResponse> list(@PageableDefault(size = 20, sort = "failedAt") Pageable pageable) {
        Page<DeadLetterResponse> page = deadLetterService.listActive(pageable);
        return new PagedModel<>(page);
    }

    /**
     * Replays a dead-lettered task: resets it to PENDING and relaunches its execution.
     * Returns 202 Accepted with the current execution snapshot.
     */
    @PostMapping("/{id}/replay")
    public ResponseEntity<ExecutionResponse> replay(@PathVariable Long id) {
        Long executionId = deadLetterService.replay(id);
        executionLauncher.launch(executionId);
        return ResponseEntity.accepted().body(executionService.get(executionId));
    }
}
