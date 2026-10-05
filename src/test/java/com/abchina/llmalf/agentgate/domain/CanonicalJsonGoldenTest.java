package com.abchina.llmalf.agentgate.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * canonical JSON / content_sha256 跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_canonical_json_golden.py 调用 Python
 * agentgate.domain.base(行为金标准)生成,Java 实现必须与样本逐字节一致;
 * 覆盖浮点 repr、码点排序、转义、大整数、嵌套结构及随机模糊样本。</p>
 */
class CanonicalJsonGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input = CanonicalJsonGoldenTest.class.getResourceAsStream("/contract/canonical-json.json")) {
            assertNotNull(input, "golden fixture /contract/canonical-json.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void samplesMatchPythonCanonicalJsonAndSha256() {
        int count = 0;
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            Object value = MAPPER.convertValue(sample.get("value"), Object.class);
            String expectedCanonical = sample.get("canonical").asText();
            String expectedSha256 = sample.get("sha256").asText();

            assertEquals(expectedCanonical, CanonicalJson.serialize(value), "canonical mismatch: " + name);
            assertEquals(expectedSha256, ContentSha256.of(value), "sha256 mismatch: " + name);
            assertEquals(expectedSha256,
                    ContentSha256.hex(ContentSha256.sha256(expectedCanonical.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                    "golden sha256 self-check failed: " + name);
            count++;
        }
        assertEquals(document.get("sample_count").asInt(), count, "sample count mismatch");
        assertTrue(count >= 25, "expected at least 25 golden samples, got " + count);
    }

    @Test
    void nonFiniteFloatsAreRejectedLikePython() {
        for (JsonNode nameNode : document.get("invalid_floats")) {
            double value;
            switch (nameNode.asText()) {
                case "double_nan":
                    value = Double.NaN;
                    break;
                case "positive_infinity":
                    value = Double.POSITIVE_INFINITY;
                    break;
                case "negative_infinity":
                    value = Double.NEGATIVE_INFINITY;
                    break;
                default:
                    throw new AssertionError("unknown invalid float kind: " + nameNode.asText());
            }
            final double input = value;
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> CanonicalJson.serialize(input));
            assertEquals("Out of range float values are not JSON compliant", error.getMessage());
        }
    }

    @Test
    void nonStringKeysAreRejected() {
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put("valid", 1);
        map.put(42, "invalid");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CanonicalJson.serialize(map));
        assertEquals("JSON object keys must be strings", error.getMessage());
    }

    @Test
    void nonJsonTypesAreRejected() {
        IllegalArgumentException object = assertThrows(IllegalArgumentException.class,
                () -> CanonicalJson.serialize(new Object()));
        assertEquals("Object of type Object is not JSON serializable", object.getMessage());

        IllegalArgumentException decimal = assertThrows(IllegalArgumentException.class,
                () -> CanonicalJson.serialize(new BigDecimal("1.5")));
        assertEquals("Object of type BigDecimal is not JSON serializable", decimal.getMessage());
    }

    @Test
    void keysSortByCodePointNotUtf16Units() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("\uffff", "uffff");
        map.put("\ud83d\ude00", "emoji");
        map.put("\ue000", "ue000");
        assertEquals("{\"\ue000\":\"ue000\",\"\uffff\":\"uffff\",\"\ud83d\ude00\":\"emoji\"}", CanonicalJson.serialize(map));
    }

    @Test
    void integerAndFloatTypesStayDistinct() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("int", 1L);
        map.put("float", 1.0d);
        assertEquals("{\"float\":1.0,\"int\":1}", CanonicalJson.serialize(map));
    }

    @Test
    void knownBoundaryValues() {
        assertEquals("null", CanonicalJson.serialize(null));
        assertEquals("{}", CanonicalJson.serialize(new LinkedHashMap<>()));
        assertEquals("[]", CanonicalJson.serialize(java.util.Collections.emptyList()));
        assertEquals("\"\\u0000\\u001f\"", CanonicalJson.serialize("\u0000\u001f"));
        assertEquals("9223372036854775807", CanonicalJson.serialize(Long.MAX_VALUE));
        assertEquals("-9223372036854775808", CanonicalJson.serialize(Long.MIN_VALUE));
        assertDoesNotThrow(() -> CanonicalJson.serialize("\u007f\u2028\u2029"));
    }
}
