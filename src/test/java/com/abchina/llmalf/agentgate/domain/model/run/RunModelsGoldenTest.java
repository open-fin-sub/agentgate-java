package com.abchina.llmalf.agentgate.domain.model.run;

import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.ContentSha256;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RunManifest/EvaluationRun 跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_run_models_golden.py 构造完整依赖链
 * (published DatasetVersion + TargetSnapshot + EvaluatorSpec + MetricPlan +
 * ReleaseGateSpec)生成:11 个合法样本(payload/canonical/sha256 三重对拍,
 * 含 transition 派生样本)与 19 个非法样本(消息逐字对齐)。</p>
 */
class RunModelsGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input =
                RunModelsGoldenTest.class.getResourceAsStream("/contract/run-models.json")) {
            assertNotNull(input, "golden fixture /contract/run-models.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void legalSamplesMatchPythonPayloadCanonicalAndSha256() {
        int manifests = 0;
        int runs = 0;
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            boolean isManifest = "manifest".equals(sample.get("union").asText());
            Map<String, Object> input = toMap(sample.get("input"));
            Object payloadTree;
            if (isManifest) {
                RunManifest manifest = RunManifest.fromPayload(input);
                assertEquals(sample.get("payload").get("manifest_sha256").asText(),
                        manifest.manifestSha256(), "manifest hash mismatch: " + name);
                payloadTree = manifest.toPayload();
                manifests++;
            } else {
                EvaluationRun run = EvaluationRun.fromPayload(input);
                for (JsonNode step : sample.get("transitions")) {
                    run = run.transition(
                            RunStatus.fromWireValue(step.get("to").asText()),
                            step.get("occurred_at").isNull()
                                    ? null : OffsetDateTime.parse(step.get("occurred_at").asText()),
                            step.get("error").isNull() ? null : step.get("error").asText());
                }
                payloadTree = run.toPayload();
                runs++;
            }
            assertEquals(sample.get("canonical").asText(),
                    CanonicalJson.serialize(payloadTree), "canonical mismatch: " + name);
            assertEquals(sample.get("sha256").asText(),
                    ContentSha256.of(payloadTree), "sha256 mismatch: " + name);
        }
        assertEquals(5, manifests, "manifest sample count");
        assertEquals(6, runs, "run sample count");
    }

    @Test
    void invalidSamplesMatchPythonErrors() {
        for (JsonNode sample : document.get("invalid_samples")) {
            String name = sample.get("name").asText();
            boolean isManifest = "manifest".equals(sample.get("union").asText());
            Map<String, Object> input = toMap(sample.get("input"));
            IllegalArgumentException error = isManifest
                    ? assertThrows(IllegalArgumentException.class,
                            () -> RunManifest.fromPayload(input), "expected failure: " + name)
                    : assertThrows(IllegalArgumentException.class,
                            () -> EvaluationRun.fromPayload(input), "expected failure: " + name);
            assertEquals(sample.get("error").asText(), error.getMessage(),
                    "wrong message for " + name);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonNode node) {
        return (Map<String, Object>) MAPPER.convertValue(node, Object.class);
    }
}
