package com.flowforge.api.controller;

import com.flowforge.api.dto.request.RouteApprovalRequest;
import com.flowforge.engine.rules.RuleOutcome;
import com.flowforge.service.rules.ApproverRoutingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the rule engine's approval-routing decision. Handy for demos/tests and shows
 * the engine as a reusable component independent of workflow execution.
 */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final ApproverRoutingService approverRoutingService;

    public RuleController(ApproverRoutingService approverRoutingService) {
        this.approverRoutingService = approverRoutingService;
    }

    @PostMapping("/route-approval")
    public RuleOutcome routeApproval(@Valid @RequestBody RouteApprovalRequest request) {
        return approverRoutingService.route(request.priority(), request.amount());
    }
}
