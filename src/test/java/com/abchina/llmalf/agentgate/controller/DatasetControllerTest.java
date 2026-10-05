package com.abchina.llmalf.agentgate.controller;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据集核心端点集成测试(共库回滚).
 *
 * <p>对齐 Python datasets.py 的 16 个非 I/O 端点:创建/详情/更新/归档/
 * 草稿生命周期/用例编辑/发布/复制;含团队隔离断言(user_team_id 不可见他队)。</p>
 */
@SpringBootTest
@Transactional
class DatasetControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TEAM_A = "it-team-a";
    private static final String TEAM_B = "it-team-b";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private String createDataset(String team) throws Exception {
        String body = "{\"name\":\"信贷数据集\",\"description\":\"描述\"}";
        MvcResult result = mockMvc.perform(post("/api/datasets")
                        .header("user_team_id", team)
                        .header("user_id", "u-1")
                        .header("user_name", "测试")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dataset.name").value("信贷数据集"))
                .andExpect(jsonPath("$.data.draft.status").value("draft"))
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString())
                .at("/data/dataset/id").asText();
    }

    private String addCase(String team, String datasetId, String caseId) throws Exception {
        String body = "{\"id\":\"" + caseId + "\",\"name\":\"用例-" + caseId + "\","
                + "\"turns\":[{\"id\":\"t-1\",\"input\":{\"q\":\"问题\"}}]}";
        return mockMvc.perform(post("/api/datasets/" + datasetId + "/drafts/cases")
                        .header("user_team_id", team)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.cases[?(@.id=='" + caseId + "')]").isArray())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void fullLifecycleCreateUpdatePublish() throws Exception {
        String datasetId = createDataset(TEAM_A);

        mockMvc.perform(get("/api/datasets/" + datasetId)
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataset.id").value(datasetId))
                .andExpect(jsonPath("$.data.versions[0].status").value("draft"));

        mockMvc.perform(patch("/api/datasets/" + datasetId)
                        .header("user_team_id", TEAM_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名数据集\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名数据集"));

        addCase(TEAM_A, datasetId, "c1");
        mockMvc.perform(post("/api/datasets/" + datasetId + "/drafts/publish")
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(get("/api/datasets/" + datasetId + "/versions/1")
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cases[0].id").value("c1"));

        mockMvc.perform(get("/api/datasets/" + datasetId + "/versions")
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("published"));

        mockMvc.perform(delete("/api/datasets/" + datasetId)
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archived").value(true));
    }

    @Test
    void teamIsolationHidesOtherTeamsData() throws Exception {
        String datasetId = createDataset(TEAM_A);

        mockMvc.perform(get("/api/datasets")
                        .header("user_team_id", TEAM_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/datasets/" + datasetId)
                        .header("user_team_id", TEAM_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("unknown Dataset: " + datasetId));

        mockMvc.perform(get("/api/datasets/" + datasetId + "/drafts/current")
                        .header("user_team_id", TEAM_B))
                .andExpect(status().isNotFound());

        MvcResult listA = mockMvc.perform(get("/api/datasets")
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = MAPPER.readTree(listA.getResponse().getContentAsString()).at("/data");
        assertTrue(items.size() >= 1, "team A must see its dataset");
    }

    @Test
    void caseEditingRejectsIdentityChangeAndUnknownCase() throws Exception {
        String datasetId = createDataset(TEAM_A);
        addCase(TEAM_A, datasetId, "c1");

        String renamed = "{\"id\":\"c1\",\"name\":\"改名\",\"turns\":[{\"id\":\"t-1\","
                + "\"input\":{\"q\":\"问题\"}}]}";
        mockMvc.perform(put("/api/datasets/" + datasetId + "/drafts/cases/c1")
                        .header("user_team_id", TEAM_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(renamed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cases[0].name").value("改名"));

        mockMvc.perform(put("/api/datasets/" + datasetId + "/drafts/cases/c1")
                        .header("user_team_id", TEAM_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"other\",\"name\":\"x\","
                                + "\"turns\":[{\"id\":\"t-1\",\"input\":{\"q\":\"p\"}}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Case ID cannot be changed"));

        mockMvc.perform(post("/api/datasets/" + datasetId + "/drafts/cases/cX/copy")
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("unknown Case: cX"));

        mockMvc.perform(post("/api/datasets/" + datasetId + "/drafts/cases/c1/copy")
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cases.length()").value(2))
                .andExpect(jsonPath("$.data.cases[1].name").value("改名（副本）"));

        mockMvc.perform(delete("/api/datasets/" + datasetId + "/drafts/cases/c1")
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cases.length()").value(1));
    }

    @Test
    void reorderRequiresExactCaseSet() throws Exception {
        String datasetId = createDataset(TEAM_A);
        addCase(TEAM_A, datasetId, "c1");
        addCase(TEAM_A, datasetId, "c2");

        mockMvc.perform(put("/api/datasets/" + datasetId + "/drafts/case-order")
                        .header("user_team_id", TEAM_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"case_ids\":[\"c2\",\"c1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cases[0].id").value("c2"));

        mockMvc.perform(put("/api/datasets/" + datasetId + "/drafts/case-order")
                        .header("user_team_id", TEAM_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"case_ids\":[\"c1\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Case order must contain every draft Case exactly once"));
    }

    @Test
    void draftLifecycleGuards() throws Exception {
        String datasetId = createDataset(TEAM_A);

        mockMvc.perform(post("/api/datasets/" + datasetId + "/drafts")
                        .header("user_team_id", TEAM_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Dataset already has an active draft"));

        mockMvc.perform(delete("/api/datasets/" + datasetId + "/drafts/current")
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/datasets/" + datasetId + "/drafts/current")
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Dataset has no active draft"));

        mockMvc.perform(post("/api/datasets/" + datasetId + "/drafts/publish")
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Dataset has no active draft"));
    }

    @Test
    void publishEmptyDraftIsRejectedWithDomainMessage() throws Exception {
        String datasetId = createDataset(TEAM_A);
        mockMvc.perform(post("/api/datasets/" + datasetId + "/drafts/publish")
                        .header("user_team_id", TEAM_A))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("published DatasetVersion requires at least one Case"));
    }

    @Test
    void copyDatasetCreatesNewDatasetWithCases() throws Exception {
        String sourceId = createDataset(TEAM_A);
        addCase(TEAM_A, sourceId, "c1");
        mockMvc.perform(post("/api/datasets/" + sourceId + "/drafts/publish")
                .header("user_team_id", TEAM_A)).andExpect(status().isOk());

        mockMvc.perform(post("/api/datasets/" + sourceId + "/copy")
                        .header("user_team_id", TEAM_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"复制数据集\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dataset.name").value("复制数据集"))
                .andExpect(jsonPath("$.data.draft.cases.length()").value(1))
                .andExpect(jsonPath("$.data.draft.notes")
                        .value("Copied from " + sourceId + " v1"));

        mockMvc.perform(post("/api/datasets/missing/copy")
                        .header("user_team_id", TEAM_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("unknown Dataset: missing"));
    }
}
