package com.abchina.llmalf.agentgate.domain.model.report;

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
 * EvaluationReport 跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_reports_golden.py 构造完整链
 * (completed Run + Results + Metrics + ReleaseGateDecision)生成:
 * 5 个合法样本与 14 个非法样本(消息逐字对齐,覆盖五段聚合校验全部分支)。</p>
 */
class ReportsGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input =
                ReportsGoldenTest.class.getResourceAsStream("/contract/reports.json")) {
            assertNotNull(input, "golden fixture /contract/reports.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void legalSamplesMatchPythonPayloadCanonicalAndSha256() {
        int count = 0;
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            Map<String, Object> input = toMap(sample.get("input"));
            EvaluationReport report = EvaluationReport.fromPayload(input);
            assertEquals(sample.get("canonical").asText(),
                    CanonicalJson.serialize(report.toPayload()), "canonical mismatch: " + name);
            assertEquals(sample.get("sha256").asText(),
                    ContentSha256.of(report.toPayload()), "sha256 mismatch: " + name);
            count++;
        }
        assertEquals(5, count);
    }

    @Test
    void invalidSamplesMatchPythonErrors() {
        for (JsonNode sample : document.get("invalid_samples")) {
            String name = sample.get("name").asText();
            Map<String, Object> input = toMap(sample.get("input"));
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> EvaluationReport.fromPayload(input), "expected failure: " + name);
            assertEquals(sample.get("error").asText(), error.getMessage(),
                    "wrong message for " + name);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonNode node) {
        return (Map<String, Object>) MAPPER.convertValue(node, Object.class);
    }
}
