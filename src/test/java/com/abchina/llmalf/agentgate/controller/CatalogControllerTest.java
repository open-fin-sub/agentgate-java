package com.abchina.llmalf.agentgate.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 目标版本目录端点测试.
 *
 * <p>对齐 Python catalogs.py:GET /api/versions 返回两个 demo 版本。</p>
 */
@SpringBootTest
class CatalogControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void versionsListMatchesCatalog() throws Exception {
        mockMvc.perform(get("/api/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].id").value("loan-agent-v1-risky"))
                .andExpect(jsonPath("$.data[0].label").value("Risky version"))
                .andExpect(jsonPath("$.data[1].id").value("loan-agent-v2-fixed"))
                .andExpect(jsonPath("$.data[1].label").value("Fixed version"));
    }
}
