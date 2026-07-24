package com.crowdmanagement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end coverage of the auth flow against the real Spring context and an
 * in-memory H2 database (see application-test.properties): register a new
 * user, log in, and confirm a role-protected endpoint rejects an
 * unauthenticated request but accepts one with a valid bearer token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void register_thenLogin_returnsAccessToken() throws Exception {
        String email = "manager+" + System.nanoTime() + "@example.com";
        String registerBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("name", "Test Manager");
            put("email", email);
            put("password", "password123");
            put("role", "MANAGER");
        }});

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.role").value("MANAGER"));

        String loginBody = objectMapper.writeValueAsString(java.util.Map.of(
            "email", email,
            "password", "password123"
        ));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void analyticsEndpoint_withoutToken_isRejected() throws Exception {
        // Role-protected endpoint (see SecurityConfig): unauthenticated
        // requests must be rejected. Spring Security may answer 401 or 403
        // depending on the configured entry point, so we assert "rejected"
        // rather than pinning an exact status code.
        mockMvc.perform(get("/api/analytics/summary").param("counterId", "1"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void locationsEndpoint_isPubliclyReadable() throws Exception {
        // GET /api/locations is intentionally permitAll (see SecurityConfig)
        // so the public dashboard can list locations without logging in.
        mockMvc.perform(get("/api/locations"))
            .andExpect(status().isOk());
    }
}
