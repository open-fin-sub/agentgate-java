package com.abchina.llmalf.agentgate.domain.model.cases;

import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.ContentSha256;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Case/CaseTurn 跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_cases_golden.py 经 pydantic
 * model_validate 生成:12 个合法样本(payload/canonical/sha256 三重对拍)
 * 与 15 个非法样本(错误消息逐字对齐)。</p>
 */
class CasesGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input = CasesGoldenTest.class.getResourceAsStream("/contract/cases.json")) {
            assertNotNull(input, "golden fixture /contract/cases.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void legalSamplesMatchPythonPayloadCanonicalAndSha256() {
        int turns = 0;
        int cases = 0;
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            boolean isTurn = "turn".equals(sample.get("union").asText());
            Map<String, Object> input = toMap(sample.get("input"));
            String actualId;
            Object payloadTree;
            if (isTurn) {
                CaseTurn turn = CaseTurn.fromPayload(input);
                actualId = turn.id();
                payloadTree = turn.toPayload();
                turns++;
            } else {
                Case model = Case.fromPayload(input);
                actualId = model.id();
                payloadTree = model.toPayload();
                cases++;
            }
            assertEquals(sample.get("payload").get("id").asText(), actualId, "id mismatch: " + name);
            assertEquals(sample.get("canonical").asText(),
                    CanonicalJson.serialize(payloadTree), "canonical mismatch: " + name);
            assertEquals(sample.get("sha256").asText(),
                    ContentSha256.of(payloadTree), "sha256 mismatch: " + name);
        }
        assertEquals(5, turns, "turn sample count");
        assertEquals(7, cases, "case sample count");
    }

    @Test
    void invalidSamplesMatchPythonErrors() {
        for (JsonNode sample : document.get("invalid_samples")) {
            String name = sample.get("name").asText();
            boolean isTurn = "turn".equals(sample.get("union").asText());
            Map<String, Object> input = toMap(sample.get("input"));
            IllegalArgumentException error = isTurn
                    ? assertThrows(IllegalArgumentException.class, () -> CaseTurn.fromPayload(input),
                            "expected failure: " + name)
                    : assertThrows(IllegalArgumentException.class, () -> Case.fromPayload(input),
                            "expected failure: " + name);
            assertEquals(sample.get("error").asText(), error.getMessage(),
                    "wrong message for " + name);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonNode node) {
        return (Map<String, Object>) MAPPER.convertValue(node, Object.class);
    }
}
