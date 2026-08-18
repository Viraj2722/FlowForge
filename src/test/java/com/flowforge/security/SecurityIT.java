package com.flowforge.security;

import com.flowforge.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end security: real login issuing a JWT, then role-based access through the
 * filter chain. Uses real HTTP semantics via MockMvc + Spring Security.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = ".+")
class SecurityIT {

    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired private WebApplicationContext context;
    @Autowired private UserService userService;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Matcher m = TOKEN.matcher(result.getResponse().getContentAsString());
        if (!m.find()) {
            throw new IllegalStateException("No token in login response");
        }
        return m.group(1);
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/workflows"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIssuesTokenThatGrantsAccessButRoleStillLimitsWrites() throws Exception {
        userService.createUser("sec-op", "sec-op@flowforge.local", "password123", Set.of("OPERATOR"));
        try {
            String token = login("sec-op", "password123");

            // Valid token -> can read own identity.
            mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username", is("sec-op")));

            // But an OPERATOR cannot author workflows (needs MANAGER/ADMIN) -> 403.
            mvc.perform(post("/api/v1/workflows")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"x\",\"priority\":\"LOW\","
                                    + "\"steps\":[{\"name\":\"s\",\"taskType\":\"CUSTOM\",\"stepOrder\":1}]}"))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("DELETE FROM users WHERE username = ?", "sec-op");
        }
    }

    @Test
    void badCredentialsAreRejectedWith401() throws Exception {
        userService.createUser("sec-viewer", "sec-viewer@flowforge.local", "password123", Set.of("VIEWER"));
        try {
            mvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"sec-viewer\",\"password\":\"WRONG\"}"))
                    .andExpect(status().isUnauthorized());
        } finally {
            jdbc.update("DELETE FROM users WHERE username = ?", "sec-viewer");
        }
    }
}
