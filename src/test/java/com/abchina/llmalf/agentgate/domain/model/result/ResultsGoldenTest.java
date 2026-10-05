package com.abchina.llmalf.agentgate.domain.model.result;

import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.ContentSha256;
import com.abchina.llmalf.agentgate.domain.model.gate.ReleaseGateDecision;
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
 * result 域跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_results_golden.py 经 pydantic
 * model_validate 生成:17 个合法样本(6 类模型三重对拍)、7 个
 * classify_release_gate 优先级分类与 36 个非法样本(消息逐字对齐)。</p>
 */
class ResultsGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input = ResultsGoldenTest.class.getResourceAsStream("/contract/results.json")) {
            assertNotNull(input, "golden fixture /contract/results.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void legalSamplesMatchPythonPayloadCanonicalAndSha256() {
        int[] counts = new int[6];
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            Map<String, Object> input = toMap(sample.get("input"));
            Object payloadTree;
            switch (sample.get("union").asText()) {
                case "method_ref":
                    payloadTree = MethodRef.fromPayload(input).toPayload();
                    counts[0]++;
                    break;
                case "judge":
                    payloadTree = JudgeRecord.fromPayload(input).toPayload();
                    counts[1]++;
                    break;
                case "error_detail":
                    payloadTree = EvaluatorErrorDetail.fromPayload(input).toPayload();
                    counts[2]++;
                    break;
                case "check":
                    payloadTree = CheckResult.fromPayload(input).toPayload();
                    counts[3]++;
                    break;
                case "result":
                    payloadTree = EvaluationResult.fromPayload(input).toPayload();
                    counts[4]++;
                    break;
                default:
                    payloadTree = ReleaseGateDecision.fromPayload(input).toPayload();
                    counts[5]++;
                    break;
            }
            assertEquals(sample.get("canonical").asText(),
                    CanonicalJson.serialize(payloadTree), "canonical mismatch: " + name);
            assertEquals(sample.get("sha256").asText(),
                    ContentSha256.of(payloadTree), "sha256 mismatch: " + name);
        }
        assertEquals(1, counts[0], "method_ref count");
        assertEquals(2, counts[1], "judge count");
        assertEquals(1, counts[2], "error_detail count");
        assertEquals(4, counts[3], "check count");
        assertEquals(6, counts[4], "result count");
        assertEquals(3, counts[5], "gate count");
    }

    @Test
    void classificationPriorityMatchesPython() {
        for (JsonNode classification : document.get("classifications")) {
            String name = classification.get("name").asText();
            Map<String, Object> input = toMap(classification.get("input"));
            ReleaseGateDecision.Classification actual = ReleaseGateDecision.classify(
                    (Boolean) input.get("has_missing_results"),
                    (Boolean) input.get("has_evaluator_errors"),
                    (Boolean) input.get("has_blocking_failures"),
                    (Boolean) input.get("has_reviews"),
                    toDouble(input.get("score")),
                    toDouble(input.get("minimum_score")));
            assertEquals(classification.get("outcome").asText(),
                    actual.outcome().wireValue(), "outcome mismatch: " + name);
            assertEquals(classification.get("reason").asText(),
                    actual.reasonCode(), "reason mismatch: " + name);
        }
    }

    @Test
    void invalidSamplesMatchPythonErrors() {
        for (JsonNode sample : document.get("invalid_samples")) {
            String name = sample.get("name").asText();
            Map<String, Object> input = toMap(sample.get("input"));
            IllegalArgumentException error = parse(sample.get("union").asText(), input);
            assertNotNull(error, "expected failure: " + name);
            assertEquals(sample.get("error").asText(), error.getMessage(),
                    "wrong message for " + name);
        }
    }

    private static Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private static IllegalArgumentException parse(String union, Map<String, Object> input) {
        try {
            switch (union) {
                case "method_ref":
                    MethodRef.fromPayload(input);
                    break;
                case "judge":
                    JudgeRecord.fromPayload(input);
                    break;
                case "error_detail":
                    EvaluatorErrorDetail.fromPayload(input);
                    break;
                case "check":
                    CheckResult.fromPayload(input);
                    break;
                case "result":
                    EvaluationResult.fromPayload(input);
                    break;
                default:
                    ReleaseGateDecision.fromPayload(input);
                    break;
            }
        } catch (IllegalArgumentException expected) {
            return expected;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonNode node) {
        return (Map<String, Object>) MAPPER.convertValue(node, Object.class);
    }
}
