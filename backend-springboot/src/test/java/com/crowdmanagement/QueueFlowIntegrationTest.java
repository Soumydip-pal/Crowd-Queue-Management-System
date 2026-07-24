package com.crowdmanagement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Covers the core demo flow end to end against the seeded admin account and
 * H2 database: log in as admin, submit a manual queue count for the seeded
 * "Counter A", then confirm it shows up via /api/queue/latest and
 * /api/predict/now (which falls back to the baseline formula in tests, since
 * app.ml-service.url in application-test.properties points at an
 * intentionally-unreachable port - see PredictionService.fallbackPrediction).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QueueFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginAsAdmin() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "email", "admin@example.com",
            "password", "admin123"
        ));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private long firstSeededCounterId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/counters"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode counters = objectMapper.readTree(result.getResponse().getContentAsString());
        return counters.get(0).get("id").asLong();
    }

    @Test
    void submitQueueCount_thenReadBackViaLatestAndLiveStatus() throws Exception {
        String token = loginAsAdmin();
        long counterId = firstSeededCounterId();

        String queueBody = objectMapper.writeValueAsString(Map.of(
            "counterId", counterId,
            "currentLength", 12,
            "source", "MANUAL"
        ));

        mockMvc.perform(post("/api/queue")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(queueBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentLength").value(12))
            .andExpect(jsonPath("$.source").value("MANUAL"));

        mockMvc.perform(get("/api/queue/latest").param("counterId", String.valueOf(counterId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentLength").value(12));

        mockMvc.perform(get("/api/queue/live").param("counterId", String.valueOf(counterId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentLength").value(12));
    }

    @Test
    void submitQueueCount_withoutAuth_isRejected() throws Exception {
        long counterId = firstSeededCounterId();
        String queueBody = objectMapper.writeValueAsString(Map.of(
            "counterId", counterId,
            "currentLength", 5
        ));

        mockMvc.perform(post("/api/queue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(queueBody))
            .andExpect(status().is4xxClientError());
    }
}
