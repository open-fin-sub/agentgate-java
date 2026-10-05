package com.abchina.llmalf.agentgate.domain.model.expectation;

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
 * Condition/Expectation 跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_expectations_golden.py 经 pydantic
 * TypeAdapter 判别验证生成:30 个合法样本(payload/canonical/sha256 三重对拍)
 * 与 17 个非法样本(错误消息对齐,判别错误仅断言抛出)。</p>
 */
class ExpectationsGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input =
                ExpectationsGoldenTest.class.getResourceAsStream("/contract/expectations.json")) {
            assertNotNull(input, "golden fixture /contract/expectations.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void legalSamplesMatchPythonPayloadCanonicalAndSha256() {
        int conditions = 0;
        int expectations = 0;
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            boolean isCondition = "condition".equals(sample.get("union").asText());
            Map<String, Object> input = toMap(sample.get("input"));
            String actualKind;
            Object payloadTree;
            if (isCondition) {
                Condition condition = Condition.fromPayload(input);
                actualKind = condition.kind();
                payloadTree = condition.toPayload();
                conditions++;
            } else {
                Expectation expectation = Expectation.fromPayload(input);
                actualKind = expectation.kind();
                payloadTree = expectation.toPayload();
                expectations++;
            }
            assertEquals(sample.get("payload").get("kind").asText(), actualKind,
                    "kind mismatch: " + name);
            assertEquals(sample.get("canonical").asText(),
                    CanonicalJson.serialize(payloadTree), "canonical mismatch: " + name);
            assertEquals(sample.get("sha256").asText(),
                    ContentSha256.of(payloadTree), "sha256 mismatch: " + name);
        }
        assertEquals(17, conditions, "condition sample count");
        assertEquals(13, expectations, "expectation sample count");
    }

    @Test
    void invalidSamplesMatchPythonErrors() {
        for (JsonNode sample : document.get("invalid_samples")) {
            String name = sample.get("name").asText();
            boolean isCondition = "condition".equals(sample.get("union").asText());
            Map<String, Object> input = toMap(sample.get("input"));
            boolean alignable = sample.get("message_alignable").asBoolean();
            IllegalArgumentException error = isCondition
                    ? assertThrows(IllegalArgumentException.class, () -> Condition.fromPayload(input),
                            "expected failure: " + name)
                    : assertThrows(IllegalArgumentException.class, () -> Expectation.fromPayload(input),
                            "expected failure: " + name);
            if (alignable) {
                assertEquals(sample.get("error").asText(), error.getMessage(),
                        "wrong message for " + name);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonNode node) {
        return (Map<String, Object>) MAPPER.convertValue(node, Object.class);
    }
}
