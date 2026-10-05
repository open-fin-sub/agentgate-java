package com.abchina.llmalf.agentgate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API Key 管理端点测试(共库回滚).
 *
 * <p>对齐 Python credentials.py:201 创建/列表/404 查询/204 删除;
 * 密钥不回显;域校验消息经 422 透出。</p>
 */
@SpringBootTest(properties = "AGENTGATE_API_KEY_ENCRYPTION_KEY=zF9UvpigZDgr61ohFelsGblCHMPBqWBjWT8wONh-QVA")
@Transactional
class CredentialControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createListGetDeleteLifecycle() throws Exception {
        String body = "{\"name\":\"主力密钥\",\"provider_id\":\"openai\","
                + "\"scope\":\"shared\",\"api_key\":\"sk-test-123\"}";
        MvcResult result = mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.name").value("主力密钥"))
                .andExpect(jsonPath("$.data.provider_id").value("openai"))
                .andExpect(jsonPath("$.data.scope").value("shared"))
                .andExpect(jsonPath("$.data.created_at").isNotEmpty())
                .andReturn();
        String apiKeyId = MAPPER.readTree(result.getResponse().getContentAsString())
                .at("/data/id").asText();

        mockMvc.perform(get("/api/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + apiKeyId + "')]").isArray());

        mockMvc.perform(get("/api/api-keys/" + apiKeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(apiKeyId));

        mockMvc.perform(delete("/api/api-keys/" + apiKeyId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/api-keys/" + apiKeyId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message").value("unknown API Key: " + apiKeyId));
    }

    @Test
    void createRejectsBlankNameWithDomainMessage() throws Exception {
        String body = "{\"name\":\"   \",\"provider_id\":\"openai\","
                + "\"scope\":\"shared\",\"api_key\":\"sk-test\"}";
        mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("ApiKeyMetadata name must not be blank"));
    }

    @Test
    void createRejectsBadScopeWithPydanticMessage() throws Exception {
        String body = "{\"name\":\"k\",\"provider_id\":\"openai\","
                + "\"scope\":\"team\",\"api_key\":\"sk-test\"}";
        mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Input should be 'shared' or 'private'"));
    }

    @Test
    void deleteMissingKeyReturns404() throws Exception {
        mockMvc.perform(delete("/api/api-keys/missing-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("unknown API Key: missing-key"));
    }
}
