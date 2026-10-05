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
 * 评测器管理端点集成测试(共库回滚).
 *
 * <p>对齐 Python evaluators.py 的 12 端点:内置目录、409 冲突、
 * 草稿生命周期、发布链、Hybrid 校验、团队隔离。</p>
 */
@SpringBootTest
@Transactional
class EvaluatorControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEAM = "it-team-ev";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private String createEvaluator(String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"描述\","
                + "\"draft\":{\"kind\":\"rule\",\"dimension\":\"state\","
                + "\"metric\":\"state_metric\",\"implementation_id\":\"final_state\"}}";
        MvcResult result = mockMvc.perform(post("/api/evaluators")
                        .header("user_team_id", TEAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.evaluator.name").value(name))
                .andExpect(jsonPath("$.data.draft.status").doesNotExist())
                .andExpect(jsonPath("$.data.draft.kind").value("rule"))
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString())
                .at("/data/evaluator/id").asText();
    }

    @Test
    void listIncludesBuiltinsBeforeUserEvaluators() throws Exception {
        mockMvc.perform(get("/api/evaluators")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='final-state')]").isArray())
                .andExpect(jsonPath("$.data[?(@.id=='policy-compliance')]").isArray())
                .andExpect(jsonPath("$.data[?(@.id=='skill-routing')].kind").value("rule"))
                .andExpect(jsonPath("$.data[?(@.id=='forbidden-tool')].severity")
                        .value("blocking"));
        MvcResult result = mockMvc.perform(get("/api/evaluators")
                        .header("user_team_id", TEAM))
                .andReturn();
        JsonNode data = MAPPER.readTree(result.getResponse().getContentAsString()).at("/data");
        assertTrue(data.size() >= 7, "builtin catalog must always be present");
    }

    @Test
    void builtinEvaluatorsAreReadOnly() throws Exception {
        mockMvc.perform(patch("/api/evaluators/final-state")
                        .header("user_team_id", TEAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("built-in Evaluator is read-only: final-state"));

        mockMvc.perform(delete("/api/evaluators/final-state")
                        .header("user_team_id", TEAM))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/evaluators/final-state/versions")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("final-state"))
                .andExpect(jsonPath("$.data[0].version").value("1"));

        mockMvc.perform(get("/api/evaluators/final-state/versions/2")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("unknown Evaluator version: final-state@2"));
    }

    @Test
    void fullLifecycleCreatePublishVersionedDraft() throws Exception {
        String evaluatorId = createEvaluator("状态评测");

        mockMvc.perform(get("/api/evaluators/" + evaluatorId)
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluator.id").value(evaluatorId))
                .andExpect(jsonPath("$.data.latest").doesNotExist())
                .andExpect(jsonPath("$.data.draft.implementation_id").value("final_state"));

        mockMvc.perform(post("/api/evaluators/" + evaluatorId + "/drafts/publish")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.content_sha256").isNotEmpty());

        mockMvc.perform(get("/api/evaluators/" + evaluatorId + "/versions/1")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dimension").value("state"));

        mockMvc.perform(get("/api/evaluators/" + evaluatorId + "/drafts/current")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Evaluator has no active draft: " + evaluatorId));

        mockMvc.perform(post("/api/evaluators/" + evaluatorId + "/drafts")
                        .header("user_team_id", TEAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.based_on_version").value("1"));

        mockMvc.perform(put("/api/evaluators/" + evaluatorId + "/drafts/current")
                        .header("user_team_id", TEAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"rule\",\"dimension\":\"state2\","
                                + "\"metric\":\"m2\",\"implementation_id\":\"final_state\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dimension").value("state2"));

        mockMvc.perform(post("/api/evaluators/" + evaluatorId + "/drafts/publish")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.dimension").value("state2"));

        mockMvc.perform(get("/api/evaluators/" + evaluatorId + "/versions")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].version").value("2"));
    }

    @Test
    void updateGuardsAndEnableRequiresPublication() throws Exception {
        String evaluatorId = createEvaluator("待启用");

        mockMvc.perform(patch("/api/evaluators/" + evaluatorId)
                        .header("user_team_id", TEAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Evaluator update must contain at least one field"));

        mockMvc.perform(patch("/api/evaluators/" + evaluatorId)
                        .header("user_team_id", TEAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":null}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Evaluator update fields cannot be null"));

        mockMvc.perform(patch("/api/evaluators/" + evaluatorId)
                        .header("user_team_id", TEAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("unpublished Evaluator cannot be enabled"));

        mockMvc.perform(patch("/api/evaluators/" + evaluatorId)
                        .header("user_team_id", TEAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名"));
    }

    private String createEvaluatorRaw(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/evaluators")
                        .header("user_team_id", TEAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString())
                .at("/data/evaluator/id").asText();
    }

    @Test
    void publishRejectsUnknownImplementationAndRuleConfig() throws Exception {
        String unknown = createEvaluatorRaw("{\"name\":\"未知实现\",\"draft\":{\"kind\":\"rule\","
                + "\"dimension\":\"d\",\"metric\":\"m\","
                + "\"implementation_id\":\"no_such_impl\"}}");
        mockMvc.perform(post("/api/evaluators/" + unknown + "/drafts/publish")
                        .header("user_team_id", TEAM))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("unknown evaluator implementation: no_such_impl@1"));

        String withConfig = createEvaluatorRaw("{\"name\":\"规则带配置\",\"draft\":{\"kind\":\"rule\","
                + "\"dimension\":\"d\",\"metric\":\"m\","
                + "\"implementation_id\":\"final_state\",\"config\":{\"x\":1}}}");
        mockMvc.perform(post("/api/evaluators/" + withConfig + "/drafts/publish")
                        .header("user_team_id", TEAM))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Rule Evaluator config must be empty"));
    }

    @Test
    void deleteRequiresUnpublishedAndTeamIsolation() throws Exception {
        String evaluatorId = createEvaluator("待删除");

        mockMvc.perform(post("/api/evaluators/" + evaluatorId + "/drafts/publish")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/evaluators/" + evaluatorId)
                        .header("user_team_id", TEAM))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("published Evaluator cannot be deleted; disable it instead"));

        mockMvc.perform(get("/api/evaluators/" + evaluatorId)
                        .header("user_team_id", "other-team"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("unknown Evaluator: " + evaluatorId));
    }
}
