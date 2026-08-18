package com.flowforge.api.validation;

import com.flowforge.api.dto.request.CreateWorkflowRequest;
import com.flowforge.api.dto.request.CreateWorkflowStepRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates {@link UniqueStepOrders}. Stateless and thread-safe (a single instance is
 * shared), which is the contract every {@link ConstraintValidator} must honour.
 */
public class UniqueStepOrdersValidator
        implements ConstraintValidator<UniqueStepOrders, CreateWorkflowRequest> {

    @Override
    public boolean isValid(CreateWorkflowRequest request, ConstraintValidatorContext context) {
        // Null / empty is not our concern here - @NotEmpty on the field handles that.
        if (request == null || request.steps() == null || request.steps().isEmpty()) {
            return true;
        }

        Set<Integer> orders = new HashSet<>();
        for (CreateWorkflowStepRequest step : request.steps()) {
            if (!orders.add(step.stepOrder())) {
                return fail(context, "duplicate stepOrder " + step.stepOrder());
            }
        }

        for (CreateWorkflowStepRequest step : request.steps()) {
            if (step.dependsOn() == null) {
                continue;
            }
            for (Integer dep : step.dependsOn()) {
                if (dep == step.stepOrder()) {
                    return fail(context, "step " + step.stepOrder() + " cannot depend on itself");
                }
                if (!orders.contains(dep)) {
                    return fail(context, "step " + step.stepOrder() + " depends on unknown stepOrder " + dep);
                }
            }
        }
        return true;
    }

    /** Replaces the default message with a specific one pointing at the {@code steps} field. */
    private boolean fail(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode("steps")
                .addConstraintViolation();
        return false;
    }
}
