package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.service.format.DatasetXlsxFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据集 I/O 端点集成测试(共库回滚).
 *
 * <p>对齐 Python datasets.py 的 import/import-xlsx/export/export-xlsx;
 * 含 Python 生成的 XLSX golden 互操作对拍。</p>
 */
@SpringBootTest
@Transactional
class DatasetIoControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEAM = "it-team-io";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private String importJsonDataset(String datasetId) throws Exception {
        String envelope = "{\"format\":\"agentgate.dataset\",\"format_version\":1,"
                + "\"dataset\":{\"id\":\"" + datasetId + "\",\"name\":\"导入数据集\","
                + "\"description\":\"\",\"archived\":false,"
                + "\"created_at\":\"2026-01-01T00:00:00Z\",\"updated_at\":\"2026-01-01T00:00:00Z\","
                + "\"user_team_id\":\"\",\"user_id\":\"\",\"user_name\":\"\"},"
                + "\"version\":{\"id\":\"v-" + datasetId + "\",\"dataset_id\":\"" + datasetId + "\","
                + "\"dataset_name\":\"导入数据集\",\"dataset_description\":\"\",\"version\":1,"
                + "\"status\":\"published\",\"based_on_version\":null,"
                + "\"cases\":[{\"id\":\"c1\",\"name\":\"用例\",\"turns\":"
                + "[{\"id\":\"t1\",\"input\":{\"q\":\"p\"},\"expectations\":[],\"notes\":\"\"}]}],"
                + "\"notes\":\"\",\"created_at\":\"2026-01-01T00:00:00Z\","
                + "\"updated_at\":\"2026-01-01T00:00:00Z\",\"published_at\":\"2026-01-01T00:00:00Z\","
                + "\"user_team_id\":\"\",\"user_id\":\"\",\"user_name\":\"\"}}";
        MvcResult result = mockMvc.perform(post("/api/datasets/import")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(envelope))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dataset.id").value(datasetId))
                .andExpect(jsonPath("$.data.version.version").value(1))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void jsonImportExportRoundTrip() throws Exception {
        importJsonDataset("it-ds-import-1");

        mockMvc.perform(post("/api/datasets/import")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"agentgate.dataset\",\"format_version\":1,"
                                + "\"dataset\":{\"id\":\"it-ds-import-1\",\"name\":\"x\"},"
                                + "\"version\":{\"id\":\"v\",\"dataset_id\":\"it-ds-import-1\"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Dataset already exists: it-ds-import-1"));

        mockMvc.perform(get("/api/datasets/it-ds-import-1/versions/1/export")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.format").value("agentgate.dataset"))
                .andExpect(jsonPath("$.data.format_version").value(1))
                .andExpect(jsonPath("$.data.dataset.id").value("it-ds-import-1"))
                .andExpect(jsonPath("$.data.version.cases[0].id").value("c1"));

        mockMvc.perform(get("/api/datasets/it-ds-import-1/versions/1/export")
                        .header("user_team_id", "other-team"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("unknown Dataset: it-ds-import-1"));
    }

    @Test
    void jsonImportRejectsBrokenEnvelopes() throws Exception {
        mockMvc.perform(post("/api/datasets/import")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"agentgate.dataset\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Dataset JSON envelope is missing: dataset, format_version, version"));

        mockMvc.perform(post("/api/datasets/import")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"agentgate.dataset\",\"format_version\":1,"
                                + "\"dataset\":{\"id\":\"a\",\"name\":\"x\"},"
                                + "\"version\":{\"id\":\"v\",\"dataset_id\":\"a\"},"
                                + "\"extra\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Dataset JSON envelope has unexpected fields: extra"));

        mockMvc.perform(post("/api/datasets/import")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"agentgate.dataset\",\"format_version\":2,"
                                + "\"dataset\":{\"id\":\"a\",\"name\":\"x\"},"
                                + "\"version\":{\"id\":\"v\",\"dataset_id\":\"a\"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("unsupported Dataset format version: 2"));

        mockMvc.perform(post("/api/datasets/import")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"other\",\"format_version\":1,"
                                + "\"dataset\":{\"id\":\"a\",\"name\":\"x\"},"
                                + "\"version\":{\"id\":\"v\",\"dataset_id\":\"a\"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("unsupported Dataset format: 'other'"));
    }

    @Test
    void pythonGeneratedXlsxParsesToExpectedCases() throws Exception {
        byte[] content;
        try (InputStream input = getClass().getResourceAsStream("/contract/sample-cases.xlsx")) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            content = buffer.toByteArray();
        }
        List<Map<String, Object>> parsed = DatasetXlsxFormat.parse(content);
        JsonNode expected;
        try (InputStream input = getClass().getResourceAsStream("/contract/expected-parse.json")) {
            expected = MAPPER.readTree(input);
        }
        assertEquals(expected.size(), parsed.size());
        for (int i = 0; i < parsed.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> expectedCase = MAPPER.convertValue(expected.get(i), Map.class);
            assertEquals(
                    com.abchina.llmalf.agentgate.domain.CanonicalJson.serialize(expectedCase),
                    com.abchina.llmalf.agentgate.domain.CanonicalJson.serialize(parsed.get(i)),
                    "case " + i + " must match Python parse output");
        }

        byte[] rewritten = DatasetXlsxFormat.dump(parsed);
        List<Map<String, Object>> reparsed = DatasetXlsxFormat.parse(rewritten);
        assertEquals(parsed.size(), reparsed.size());
        for (int i = 0; i < parsed.size(); i++) {
            assertEquals(
                    com.abchina.llmalf.agentgate.domain.CanonicalJson.serialize(parsed.get(i)),
                    com.abchina.llmalf.agentgate.domain.CanonicalJson.serialize(reparsed.get(i)),
                    "dump/parse round trip must be stable");
        }
    }

    @Test
    void xlsxImportEndpointAcceptsPythonGeneratedFile() throws Exception {
        byte[] content;
        try (InputStream input = getClass().getResourceAsStream("/contract/sample-cases.xlsx")) {
            content = org.springframework.util.StreamUtils.copyToByteArray(input);
        }
        MockMultipartFile file = new MockMultipartFile("file", "cases.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
        mockMvc.perform(multipart("/api/datasets/import/xlsx")
                        .file(file)
                        .param("name", "XLSX导入")
                        .param("description", "描述")
                        .header("user_team_id", TEAM))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dataset.name").value("XLSX导入"))
                .andExpect(jsonPath("$.data.version.cases.length()").value(2))
                .andExpect(jsonPath("$.data.version.cases[0].id").value("c1"))
                .andExpect(jsonPath("$.data.version.cases[0].turns.length()").value(2))
                .andExpect(jsonPath("$.data.version.cases[0].tags[0]").value("核心"));
    }

    @Test
    void xlsxImportRejectsBadFilenameAndMissingSheet() throws Exception {
        byte[] content = new byte[] {1, 2, 3};
        MockMultipartFile wrongName = new MockMultipartFile("file", "cases.csv", null, content);
        mockMvc.perform(multipart("/api/datasets/import/xlsx")
                        .file(wrongName)
                        .param("name", "x")
                        .header("user_team_id", TEAM))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message").value("xlsx_validation_failed"))
                .andExpect(jsonPath("$.data.issues[0].message")
                        .value("file must have a .xlsx filename"));

        MockMultipartFile notArchive = new MockMultipartFile("file", "cases.xlsx", null, content);
        mockMvc.perform(multipart("/api/datasets/import/xlsx")
                        .file(notArchive)
                        .param("name", "x")
                        .header("user_team_id", TEAM))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.issues[0].message")
                        .value("file is not a valid XLSX archive"));
    }

    @Test
    void xlsxExportReturnsFileWithIntegrityHeaders() throws Exception {
        importJsonDataset("it-ds-export-1");
        MvcResult result = mockMvc.perform(get(
                        "/api/datasets/it-ds-export-1/versions/1/export/xlsx")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, immutable"))
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"dataset-v1.xlsx\""))
                .andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        assertTrue(body.length > 0);

        List<Map<String, Object>> parsed = DatasetXlsxFormat.parse(body);
        assertEquals(1, parsed.size());
        assertEquals("c1", parsed.get(0).get("id"));
    }
}
