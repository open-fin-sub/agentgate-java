package com.abchina.llmalf.agentgate.domain.model.credential;

import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.ContentSha256;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ApiKeyMetadata 跨语言 golden 对拍与聚焦行为测试.
 *
 * <p>金样本由 scripts/generate_credentials_golden.py 经 pydantic
 * model_validate 生成:4 个合法样本(payload/canonical/sha256 三重对拍)
 * 与 7 个非法样本(消息逐字对齐)。</p>
 */
class ApiKeyMetadataTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input =
                ApiKeyMetadataTest.class.getResourceAsStream("/contract/credentials.json")) {
            assertNotNull(input, "golden fixture /contract/credentials.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void legalSamplesMatchPythonPayloadCanonicalAndSha256() {
        int count = 0;
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            Map<String, Object> input = toMap(sample.get("input"));
            ApiKeyMetadata model = ApiKeyMetadata.fromPayload(input);
            assertEquals(sample.get("payload").get("id").asText(), model.id(),
                    "id mismatch: " + name);
            assertEquals(sample.get("canonical").asText(),
                    CanonicalJson.serialize(model.toPayload()), "canonical mismatch: " + name);
            assertEquals(sample.get("sha256").asText(),
                    ContentSha256.of(model.toPayload()), "sha256 mismatch: " + name);
            count++;
        }
        assertEquals(4, count);
    }

    @Test
    void invalidSamplesMatchPythonErrors() {
        for (JsonNode sample : document.get("invalid_samples")) {
            String name = sample.get("name").asText();
            Map<String, Object> input = toMap(sample.get("input"));
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ApiKeyMetadata.fromPayload(input), "expected failure: " + name);
            assertEquals(sample.get("error").asText(), error.getMessage(),
                    "wrong message for " + name);
        }
    }

    @Test
    void nullScopeAndTimestampsFallBackToDefaults() {
        assertEquals("Field required",
                assertThrows(IllegalArgumentException.class, () -> ApiKeyMetadata.of(
                        "k", "n", "p", null, null, null)).getMessage());

        ApiKeyMetadata metadata = ApiKeyMetadata.of("k", "n", "p", ApiKeyScope.SHARED,
                null, null);
        assertEquals(ZoneOffset.UTC, metadata.createdAt().getOffset());
        assertTrue(!metadata.updatedAt().isBefore(metadata.createdAt()));
    }

    @Test
    void fromPayloadGeneratesIdWhenMissing() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "新密钥");
        payload.put("provider_id", "openai");
        payload.put("scope", "private");
        ApiKeyMetadata first = ApiKeyMetadata.fromPayload(payload);
        ApiKeyMetadata second = ApiKeyMetadata.fromPayload(payload);
        assertTrue(!first.id().equals(second.id()), "generated ids must differ");
        assertEquals(ApiKeyScope.PRIVATE, first.scope());
    }

    @Test
    void scopeWireValuesRoundTrip() {
        assertEquals(ApiKeyScope.SHARED, ApiKeyScope.fromWireValue("shared"));
        assertEquals(ApiKeyScope.PRIVATE, ApiKeyScope.fromWireValue("private"));
        assertEquals("Input should be 'shared' or 'private'",
                assertThrows(IllegalArgumentException.class,
                        () -> ApiKeyScope.fromWireValue("team")).getMessage());
        assertEquals("Input should be 'shared' or 'private'",
                assertThrows(IllegalArgumentException.class,
                        () -> ApiKeyScope.fromWireValue(null)).getMessage());
    }

    @Test
    void nonStringScopeIsRejected() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "k");
        payload.put("name", "n");
        payload.put("provider_id", "p");
        payload.put("scope", 42);
        assertEquals("Input should be 'shared' or 'private'",
                assertThrows(IllegalArgumentException.class,
                        () -> ApiKeyMetadata.fromPayload(payload)).getMessage());
    }

    private static OffsetDateTime time(String value) {
        return OffsetDateTime.parse(value);
    }

    @Test
    void identityValidation() {
        assertEquals("ApiKeyMetadata name must not be blank",
                assertThrows(IllegalArgumentException.class, () -> ApiKeyMetadata.of(
                        "k", " ", "p", ApiKeyScope.SHARED, null, null)).getMessage());
        assertEquals("ApiKeyMetadata provider_id must not be blank",
                assertThrows(IllegalArgumentException.class, () -> ApiKeyMetadata.of(
                        "k", "n", " ", ApiKeyScope.SHARED, null, null)).getMessage());
        assertEquals("ApiKeyMetadata id must not be blank",
                assertThrows(IllegalArgumentException.class, () -> ApiKeyMetadata.of(
                        null, "n", "p", ApiKeyScope.SHARED, null, null)).getMessage());
        assertEquals("updated_at must not precede created_at",
                assertThrows(IllegalArgumentException.class, () -> ApiKeyMetadata.of(
                        "k", "n", "p", ApiKeyScope.SHARED,
                        time("2026-01-02T00:00:00+00:00"),
                        time("2026-01-01T00:00:00+00:00"))).getMessage());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonNode node) {
        return (Map<String, Object>) MAPPER.convertValue(node, Object.class);
    }
}
