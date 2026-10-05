package com.abchina.llmalf.agentgate.domain.model.trace;

import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.ContentSha256;
import com.abchina.llmalf.agentgate.domain.model.artifact.Artifact;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask;
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
 * Trace/TraceSpan/Artifact/EvaluationTask 跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_trace_artifacts_tasks_golden.py 经 pydantic
 * model_validate 生成:10 个合法样本与 27 个非法样本(消息逐字对齐)。</p>
 */
class TraceArtifactsTasksGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input = TraceArtifactsTasksGoldenTest.class
                .getResourceAsStream("/contract/trace-artifacts-tasks.json")) {
            assertNotNull(input, "golden fixture /contract/trace-artifacts-tasks.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void legalSamplesMatchPythonPayloadCanonicalAndSha256() {
        int[] counts = new int[4];
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            Map<String, Object> input = toMap(sample.get("input"));
            Object payloadTree;
            switch (sample.get("union").asText()) {
                case "span":
                    payloadTree = TraceSpan.fromPayload(input).toPayload();
                    counts[0]++;
                    break;
                case "trace":
                    payloadTree = Trace.fromPayload(input).toPayload();
                    counts[1]++;
                    break;
                case "artifact":
                    payloadTree = Artifact.fromPayload(input).toPayload();
                    counts[2]++;
                    break;
                default:
                    payloadTree = EvaluationTask.fromPayload(input).toPayload();
                    counts[3]++;
                    break;
            }
            assertEquals(sample.get("canonical").asText(),
                    CanonicalJson.serialize(payloadTree), "canonical mismatch: " + name);
            assertEquals(sample.get("sha256").asText(),
                    ContentSha256.of(payloadTree), "sha256 mismatch: " + name);
        }
        assertEquals(2, counts[0], "span count");
        assertEquals(3, counts[1], "trace count");
        assertEquals(2, counts[2], "artifact count");
        assertEquals(3, counts[3], "task count");
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

    private static IllegalArgumentException parse(String union, Map<String, Object> input) {
        try {
            switch (union) {
                case "span":
                    TraceSpan.fromPayload(input);
                    break;
                case "trace":
                    Trace.fromPayload(input);
                    break;
                case "artifact":
                    Artifact.fromPayload(input);
                    break;
                default:
                    EvaluationTask.fromPayload(input);
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
