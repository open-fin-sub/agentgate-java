package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.ContentSha256;
import com.abchina.llmalf.agentgate.domain.model.evaluator.Evaluator;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorDraft;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
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
 * EvaluatorVersioning.publish 跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_evaluator_publications_golden.py 调用
 * Python evaluator/versioning.py::publish_evaluator_draft 生成:
 * 5 个合法组装(payload/canonical/sha256 三重对拍)与 4 个非法
 * (消息逐字对齐)。</p>
 */
class EvaluatorVersioningGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input = EvaluatorVersioningGoldenTest.class
                .getResourceAsStream("/contract/evaluator-publications.json")) {
            assertNotNull(input, "golden fixture /contract/evaluator-publications.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void legalPublicationsMatchPython() {
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            Evaluator evaluator = Evaluator.fromPayload(toMap(sample.get("evaluator")));
            EvaluatorDraft draft = EvaluatorDraft.fromPayload(toMap(sample.get("draft")));
            EvaluatorSpec spec = EvaluatorVersioning.publish(evaluator, draft,
                    sample.get("version").asLong());
            assertEquals(sample.get("payload").get("version").asText(), spec.version(),
                    "version mismatch: " + name);
            assertEquals(sample.get("canonical").asText(),
                    CanonicalJson.serialize(spec.toPayload()), "canonical mismatch: " + name);
            assertEquals(sample.get("sha256").asText(),
                    ContentSha256.of(spec.toPayload()), "sha256 mismatch: " + name);
        }
    }

    @Test
    void invalidPublicationsMatchPythonErrors() {
        for (JsonNode sample : document.get("invalid_samples")) {
            String name = sample.get("name").asText();
            Evaluator evaluator = Evaluator.fromPayload(toMap(sample.get("evaluator")));
            EvaluatorDraft draft = EvaluatorDraft.fromPayload(toMap(sample.get("draft")));
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> EvaluatorVersioning.publish(evaluator, draft,
                            sample.get("version").asLong()),
                    "expected failure: " + name);
            assertEquals(sample.get("error").asText(), error.getMessage(),
                    "wrong message for " + name);
        }
    }

    @Test
    void versionParsingRules() {
        assertEquals(1L, EvaluatorVersioning.parseVersion("1"));
        assertEquals(42L, EvaluatorVersioning.parseVersion("42"));
        assertEquals("Evaluator version must be a canonical positive integer",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluatorVersioning.parseVersion("1x")).getMessage());
        assertEquals("Evaluator version must be a canonical positive integer",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluatorVersioning.parseVersion("")).getMessage());
        assertEquals("Evaluator version must be a canonical positive BIGINT",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluatorVersioning.parseVersion("0")).getMessage());
        assertEquals("Evaluator version must be a canonical positive BIGINT",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluatorVersioning.parseVersion(
                                "9223372036854775808")).getMessage());
        assertEquals("Evaluator version must be a canonical positive BIGINT",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluatorVersioning.parseVersion("01")).getMessage());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonNode node) {
        return (Map<String, Object>) MAPPER.convertValue(node, Object.class);
    }
}
