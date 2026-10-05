package com.abchina.llmalf.agentgate.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API Key 管理不可用场景(未配置主密钥,对齐 Python api_keys=None → 503)。
 */
@SpringBootTest
class CredentialControllerUnavailableTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void allEndpointsReturn503WithoutMasterKey() throws Exception {
        String body = "{\"name\":\"k\",\"provider_id\":\"openai\","
                + "\"scope\":\"shared\",\"api_key\":\"sk\"}";
        mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("API Key management is unavailable"));

        mockMvc.perform(get("/api/api-keys"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/api-keys/any"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(delete("/api/api-keys/any"))
                .andExpect(status().isServiceUnavailable());
    }
}
