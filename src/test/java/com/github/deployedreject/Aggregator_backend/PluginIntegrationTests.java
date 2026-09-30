package com.github.deployedreject.Aggregator_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.deployedreject.Aggregator_backend.dto.PluginSubmitRequest;
import com.github.deployedreject.Aggregator_backend.dto.RateRequest;
import com.github.deployedreject.Aggregator_backend.entity.VoteType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class PluginIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.github.deployedreject.Aggregator_backend.repository.PluginRepository pluginRepository;

    @Autowired
    private com.github.deployedreject.Aggregator_backend.repository.PluginRatingRepository ratingRepository;

    @Autowired
    private com.github.deployedreject.Aggregator_backend.repository.ModerationAlertRepository alertRepository;

    @org.junit.jupiter.api.BeforeAll
    void cleanUpDatabase() {
        ratingRepository.deleteAll();
        alertRepository.deleteAll();
        pluginRepository.findById("testplugin").ifPresent(pluginRepository::delete);
        pluginRepository.findById("gemini-anime").ifPresent(pluginRepository::delete);
    }

    @Test
    @Order(1)
    void testSubmitPlugin() throws Exception {
        PluginSubmitRequest request = new PluginSubmitRequest(
            "testplugin",
            "Test Plugin",
            "A test anime plugin",
            "https://test.anime.com",
            "1.0.0",
            "AuthorTest",
            "console.log('hello from test plugin');"
        );

        mockMvc.perform(post("/api/v1/plugins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is("testplugin")))
            .andExpect(jsonPath("$.channel", is("NIGHTLY")))
            .andExpect(jsonPath("$.likes", is(0)))
            .andExpect(jsonPath("$.dislikes", is(0)));
    }

    @Test
    @Order(2)
    void testDownloadPluginCode() throws Exception {
        mockMvc.perform(get("/api/v1/plugins/testplugin/download"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/javascript"))
            .andExpect(content().string(containsString("hello from test plugin")));
    }

    @Test
    @Order(3)
    void testListPluginsNightly() throws Exception {
        mockMvc.perform(get("/api/v1/plugins")
                .param("channel", "NIGHTLY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.content[0].id", is("testplugin")));
    }

    @Test
    @Order(4)
    void testRatePluginDeduplication() throws Exception {
        RateRequest likeVote = new RateRequest(VoteType.LIKE);

        // First vote from IP 1.2.3.4 -> likes = 1
        mockMvc.perform(post("/api/v1/plugins/testplugin/rate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "1.2.3.4")
                .content(objectMapper.writeValueAsString(likeVote)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.likes", is(1)))
            .andExpect(jsonPath("$.dislikes", is(0)));

        // Duplicate vote from same IP -> likes stays 1 (deduplicated)
        mockMvc.perform(post("/api/v1/plugins/testplugin/rate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "1.2.3.4")
                .content(objectMapper.writeValueAsString(likeVote)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.likes", is(1)))
            .andExpect(jsonPath("$.dislikes", is(0)));

        // Flip vote to DISLIKE from same IP -> likes = 0, dislikes = 1
        RateRequest dislikeVote = new RateRequest(VoteType.DISLIKE);
        mockMvc.perform(post("/api/v1/plugins/testplugin/rate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "1.2.3.4")
                .content(objectMapper.writeValueAsString(dislikeVote)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.likes", is(0)))
            .andExpect(jsonPath("$.dislikes", is(1)));
    }

    @Test
    @Order(5)
    void testLlmGatewayPublish() throws Exception {
        PluginSubmitRequest llmRequest = new PluginSubmitRequest(
            "gemini-anime",
            "Gemini Auto Scraped",
            "Generated by Gemini loop",
            "https://gemini.stream.com",
            "0.1.0",
            null,
            "export default class GeminiStream {}"
        );

        mockMvc.perform(post("/api/v1/internal/llm/publish")
                .header("X-API-Key", "dev-llm-token-12345")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(llmRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is("gemini-anime")))
            .andExpect(jsonPath("$.author", is("AI:Gemini")))
            .andExpect(jsonPath("$.channel", is("NIGHTLY")));
    }

    @Test
    @Order(6)
    void testAdminEndpoints() throws Exception {
        // 1. Unauthorized without token -> 401
        mockMvc.perform(get("/api/v1/admin/plugins"))
            .andExpect(status().isUnauthorized());

        // 2. Authorized listing -> 200 OK
        mockMvc.perform(get("/api/v1/admin/plugins")
                .header("X-Admin-Token", "aggregator-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // 3. Promote testplugin to STABLE
        mockMvc.perform(put("/api/v1/admin/plugins/testplugin/channel")
                .param("channel", "STABLE")
                .header("X-Admin-Token", "aggregator-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.channel", is("STABLE")));

        // 4. Toggle active status
        mockMvc.perform(put("/api/v1/admin/plugins/testplugin/toggle-active")
                .header("X-Admin-Token", "aggregator-admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive", is(false)));
    }
}
