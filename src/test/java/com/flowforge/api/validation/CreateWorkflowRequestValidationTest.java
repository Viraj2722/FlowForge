package com.flowforge.api.validation;

import com.flowforge.api.dto.request.CreateWorkflowRequest;
import com.flowforge.api.dto.request.CreateWorkflowStepRequest;
import com.flowforge.engine.model.Priority;
import com.flowforge.engine.model.TaskType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure (no Spring) unit test of the {@link UniqueStepOrders} constraint, driving the
 * Bean Validation API directly. Fast and framework-free.
 */
class CreateWorkflowRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private CreateWorkflowStepRequest step(String name, int order, List<Integer> dependsOn) {
        return new CreateWorkflowStepRequest(name, TaskType.CUSTOM, order, Map.of(), null, dependsOn);
    }

    @Test
    void validRequestHasNoViolations() {
        CreateWorkflowRequest req = new CreateWorkflowRequest("wf", null, Priority.LOW,
                List.of(step("a", 1, null), step("b", 2, List.of(1))));
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void duplicateStepOrderIsRejected() {
        CreateWorkflowRequest req = new CreateWorkflowRequest("wf", null, Priority.LOW,
                List.of(step("a", 1, null), step("b", 1, null)));
        assertThat(validator.validate(req))
                .anyMatch(v -> v.getMessage().contains("duplicate stepOrder"));
    }

    @Test
    void dependsOnUnknownStepIsRejected() {
        CreateWorkflowRequest req = new CreateWorkflowRequest("wf", null, Priority.LOW,
                List.of(step("a", 1, List.of(99))));
        assertThat(validator.validate(req))
                .anyMatch(v -> v.getMessage().contains("unknown stepOrder"));
    }

    @Test
    void blankStepNameIsRejectedByFieldConstraint() {
        CreateWorkflowRequest req = new CreateWorkflowRequest("wf", null, Priority.LOW,
                List.of(step("", 1, null)));
        // @NotBlank on the nested step name, reached via List<@Valid ...>
        assertThat(validator.validate(req)).isNotEmpty();
    }
}
