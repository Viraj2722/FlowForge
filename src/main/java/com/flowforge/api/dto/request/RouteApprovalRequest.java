package com.flowforge.api.dto.request;

import com.flowforge.engine.model.Priority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Inputs for an approval-routing decision.
 */
public record RouteApprovalRequest(
        @NotNull Priority priority,
        @NotNull @PositiveOrZero BigDecimal amount
) {
}
