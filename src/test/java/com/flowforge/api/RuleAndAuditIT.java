package com.flowforge.api;

import com.flowforge.api.dto.request.CreateWorkflowRequest;
import com.flowforge.api.dto.request.CreateWorkflowStepRequest;
import com.flowforge.engine.model.Priority;
import com.flowforge.engine.model.TaskType;
import com.flowforge.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 8: the rule-routing endpoint (through the web stack) and the event-driven audit
 * pipeline (a committed workflow-create must leave a WORKFLOW_CREATED audit row).
 */
@SpringBootTest
@WithMockUser(username = "rule-tester", roles = {"VIEWER"})
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = ".+")
class RuleAndAuditIT {

    @Autowired private WebApplicationContext context;
    @Autowired private WorkflowService workflowService;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void ruleEndpointRoutesHighValueToSenior() throws Exception {
        mvc.perform(post("/api/v1/rules/route-approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"HIGH\", \"amount\": 200000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("SENIOR_APPROVER")));
    }

    @Test
    void creatingAWorkflowWritesAnAuditRecordAfterCommit() {
        // Not @Transactional: create() commits, so the AFTER_COMMIT audit listener fires.
        long id = workflowService.create(new CreateWorkflowRequest(
                "audit-wf", null, Priority.MEDIUM,
                List.of(new CreateWorkflowStepRequest("s", TaskType.CUSTOM, 1, Map.of(), null, null)))).id();
        try {
            Long count = jdbc.queryForObject(
                    "SELECT count(*) FROM audit_logs WHERE action = 'WORKFLOW_CREATED' AND entity_id = ?",
                    Long.class, id);
            assertThat(count).isEqualTo(1L);
        } finally {
            jdbc.update("DELETE FROM audit_logs WHERE entity_type = 'WORKFLOW' AND entity_id = ?", id);
            jdbc.update("DELETE FROM workflows WHERE id = ?", id);
        }
    }
}
