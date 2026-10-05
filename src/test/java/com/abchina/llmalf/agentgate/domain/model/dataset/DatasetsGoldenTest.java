package com.abchina.llmalf.agentgate.domain.model.dataset;

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
 * Dataset/DatasetVersion 跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_datasets_golden.py 经 pydantic
 * model_validate 生成:13 个合法样本(payload/canonical/sha256 三重对拍,
 * 含 Z 后缀时间序列化、offset 归一、微秒补零、content hash 语义)
 * 与 20 个非法样本(19 条消息逐字对齐)。</p>
 */
class DatasetsGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input = DatasetsGoldenTest.class.getResourceAsStream("/contract/datasets.json")) {
            assertNotNull(input, "golden fixture /contract/datasets.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void legalSamplesMatchPythonPayloadCanonicalAndSha256() {
        int datasets = 0;
        int versions = 0;
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            boolean isDataset = "dataset".equals(sample.get("union").asText());
            Map<String, Object> input = toMap(sample.get("input"));
            Object payloadTree;
            if (isDataset) {
                Dataset model = Dataset.fromPayload(input);
                payloadTree = model.toPayload();
                datasets++;
            } else {
                DatasetVersion model = DatasetVersion.fromPayload(input);
                assertEquals(sample.get("payload").get("content_sha256").asText(),
                        model.contentSha256(), "content hash mismatch: " + name);
                payloadTree = model.toPayload();
                versions++;
            }
            assertEquals(sample.get("canonical").asText(),
                    CanonicalJson.serialize(payloadTree), "canonical mismatch: " + name);
            assertEquals(sample.get("sha256").asText(),
                    ContentSha256.of(payloadTree), "sha256 mismatch: " + name);
        }
        assertEquals(3, datasets, "dataset sample count");
        assertEquals(10, versions, "version sample count");
    }

    @Test
    void invalidSamplesMatchPythonErrors() {
        for (JsonNode sample : document.get("invalid_samples")) {
            String name = sample.get("name").asText();
            boolean isDataset = "dataset".equals(sample.get("union").asText());
            Map<String, Object> input = toMap(sample.get("input"));
            IllegalArgumentException error = isDataset
                    ? assertThrows(IllegalArgumentException.class, () -> Dataset.fromPayload(input),
                            "expected failure: " + name)
                    : assertThrows(IllegalArgumentException.class, () -> DatasetVersion.fromPayload(input),
                            "expected failure: " + name);
            if (sample.get("message_alignable").asBoolean()) {
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
