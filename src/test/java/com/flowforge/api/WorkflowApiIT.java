package com.flowforge.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API test through the real Spring MVC stack (MockMvc) and real PostgreSQL.
 * Gated on {@code DB_NAME}; {@code @Transactional} rolls back all writes.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "api-tester", roles = {"ADMIN"}) // authorized for all workflow operations
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = ".+")
class WorkflowApiIT {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Build MockMvc from the full web context, with Spring Security wired in.
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static final String CREATE_BODY = """
            {
              "name": "Onboarding",
              "description": "New customer onboarding",
              "priority": "HIGH",
              "steps": [
                {"name": "call-crm", "taskType": "WEBHOOK", "stepOrder": 1, "parameters": {"url": "http://crm"}},
                {"name": "notify",   "taskType": "EMAIL",   "stepOrder": 2, "parameters": {"to": "a@b.com"}, "dependsOn": [1]}
              ]
            }
            """;

    @Test
    void createWorkflowReturns201WithLocationAndSteps() throws Exception {
        mvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.steps", hasSize(2)))
                .andExpect(jsonPath("$.steps[0].name", is("call-crm")))
                .andExpect(jsonPath("$.steps[1].dependsOn[0]", is(1)));
    }

    @Test
    void invalidRequestReturns400WithFieldErrors() throws Exception {
        String bad = """
                {"name": "", "priority": "HIGH", "steps": []}
                """;
        mvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors", hasSize(2))); // name + steps
    }

    @Test
    void getMissingWorkflowReturns404() throws Exception {
        mvc.perform(get("/api/v1/workflows/{id}", 99_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void triggeringInactiveWorkflowReturns409() throws Exception {
        long id = createOnboarding();
        // Fresh workflow is DRAFT, not ACTIVE -> trigger must be rejected with 409.
        mvc.perform(post("/api/v1/workflows/{id}/executions", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void fullFlow_activateThenTriggerCreatesPendingTasks() throws Exception {
        long id = createOnboarding();

        // Activate it.
        mvc.perform(put("/api/v1/workflows/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        // Trigger -> 202 Accepted, one PENDING task per step.
        mvc.perform(post("/api/v1/workflows/{id}/executions", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.tasks", hasSize(2)))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
    }

    private long createOnboarding() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        // crude id extraction to avoid coupling the test to a JSON lib
        int idx = json.indexOf("\"id\":");
        int start = idx + 5;
        int end = json.indexOf(',', start);
        return Long.parseLong(json.substring(start, end).trim());
    }
}
