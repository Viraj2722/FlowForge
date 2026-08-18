package com.flowforge.observability;

import com.flowforge.api.dto.request.CreateWorkflowRequest;
import com.flowforge.api.dto.request.CreateWorkflowStepRequest;
import com.flowforge.api.dto.request.UpdateWorkflowRequest;
import com.flowforge.domain.enums.WorkflowStatus;
import com.flowforge.engine.model.Priority;
import com.flowforge.engine.model.TaskType;
import com.flowforge.service.ExecutionService;
import com.flowforge.service.WorkflowService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 11: the health endpoint is public and reports UP, and our custom Micrometer
 * metric increments when an execution is triggered.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = ".+")
class ObservabilityIT {

    @Autowired private WebApplicationContext context;
    @Autowired private WorkflowService workflowService;
    @Autowired private ExecutionService executionService;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void healthEndpointIsPublicAndReportsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void triggeringAnExecutionIncrementsTheCounter() {
        double before = meterRegistry.counter("flowforge.executions.triggered").count();

        long wfId = workflowService.create(new CreateWorkflowRequest(
                "metrics-wf", null, Priority.LOW,
                List.of(new CreateWorkflowStepRequest("s", TaskType.CUSTOM, 1, Map.of(), null, null)))).id();
        workflowService.update(wfId, new UpdateWorkflowRequest(null, null, null, WorkflowStatus.ACTIVE));
        try {
            executionService.trigger(wfId, "test");
            double after = meterRegistry.counter("flowforge.executions.triggered").count();
            assertThat(after).isEqualTo(before + 1.0);
        } finally {
            jdbc.update("DELETE FROM workflows WHERE id = ?", wfId);
        }
    }
}
