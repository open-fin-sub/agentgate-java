package com.abchina.llmalf.agentgate.domain.model.evaluator;

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
 * Evaluator 系列跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_evaluators_golden.py 经 pydantic
 * model_validate 生成:15 个合法样本(ref/evaluator/draft/spec 四类,
 * payload/canonical/sha256 三重对拍)与 30 个非法样本(消息逐字对齐)。</p>
 */
class EvaluatorsGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input =
                EvaluatorsGoldenTest.class.getResourceAsStream("/contract/evaluators.json")) {
            assertNotNull(input, "golden fixture /contract/evaluators.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void legalSamplesMatchPythonPayloadCanonicalAndSha256() {
        int[] counts = new int[4];
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            String union = sample.get("union").asText();
            Map<String, Object> input = toMap(sample.get("input"));
            Object payloadTree;
            switch (union) {
                case "ref":
                    EvaluatorRef ref = EvaluatorRef.fromPayload(input);
                    assertEquals(sample.get("payload").get("evaluator_id").asText(),
                            ref.evaluatorId(), "id mismatch: " + name);
                    payloadTree = ref.toPayload();
                    counts[0]++;
                    break;
                case "evaluator":
                    Evaluator evaluator = Evaluator.fromPayload(input);
                    assertEquals(sample.get("payload").get("id").asText(), evaluator.id(),
                            "id mismatch: " + name);
                    payloadTree = evaluator.toPayload();
                    counts[1]++;
                    break;
                case "draft":
                    EvaluatorDraft draft = EvaluatorDraft.fromPayload(input);
                    assertEquals(sample.get("payload").get("id").asText(), draft.id(),
                            "id mismatch: " + name);
                    payloadTree = draft.toPayload();
                    counts[2]++;
                    break;
                default:
                    EvaluatorSpec spec = EvaluatorSpec.fromPayload(input);
                    assertEquals(sample.get("payload").get("id").asText(), spec.id(),
                            "id mismatch: " + name);
                    assertEquals(sample.get("payload").get("content_sha256").asText(),
                            spec.contentSha256(), "content hash mismatch: " + name);
                    payloadTree = spec.toPayload();
                    counts[3]++;
                    break;
            }
            assertEquals(sample.get("canonical").asText(),
                    CanonicalJson.serialize(payloadTree), "canonical mismatch: " + name);
            assertEquals(sample.get("sha256").asText(),
                    ContentSha256.of(payloadTree), "sha256 mismatch: " + name);
        }
        assertEquals(2, counts[0], "ref sample count");
        assertEquals(3, counts[1], "evaluator sample count");
        assertEquals(5, counts[2], "draft sample count");
        assertEquals(5, counts[3], "spec sample count");
    }

    @Test
    void invalidSamplesMatchPythonErrors() {
        for (JsonNode sample : document.get("invalid_samples")) {
            String name = sample.get("name").asText();
            String union = sample.get("union").asText();
            Map<String, Object> input = toMap(sample.get("input"));
            IllegalArgumentException error = parse(union, input);
            assertNotNull(error, "expected failure: " + name);
            if (sample.get("message_alignable").asBoolean()) {
                assertEquals(sample.get("error").asText(), error.getMessage(),
                        "wrong message for " + name);
            }
        }
    }

    private static IllegalArgumentException parse(String union, Map<String, Object> input) {
        try {
            switch (union) {
                case "ref":
                    EvaluatorRef.fromPayload(input);
                    break;
                case "evaluator":
                    Evaluator.fromPayload(input);
                    break;
                case "draft":
                    EvaluatorDraft.fromPayload(input);
                    break;
                default:
                    EvaluatorSpec.fromPayload(input);
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
