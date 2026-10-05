package com.abchina.llmalf.agentgate.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存储身份摘要跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_identity_digests_golden.py 调用 Python
 * agentgate.storage.mysql._digest/_target_key 生成:12 个单 part 摘要与
 * 4 个 TargetRef 四元组摘要,Java 侧逐字节一致。</p>
 */
class IdentityDigestGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input =
                IdentityDigestGoldenTest.class.getResourceAsStream("/contract/identity-digests.json")) {
            assertNotNull(input, "golden fixture /contract/identity-digests.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void singlePartDigestsMatchPythonByteForByte() {
        int count = 0;
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            String[] parts = toArray(sample.get("parts"));
            byte[] expected = hexToBytes(sample.get("digest_hex").asText());
            assertArrayEquals(expected, IdentityDigest.of(parts),
                    "digest mismatch: " + name);
            assertEquals(sample.get("digest_hex").asText(), IdentityDigest.ofHex(parts),
                    "hex mismatch: " + name);
            count++;
        }
        assertEquals(12, count);
    }

    @Test
    void targetKeyDigestsMatchPython() {
        for (JsonNode sample : document.get("target_key_samples")) {
            String name = sample.get("name").asText();
            String[] parts = toArray(sample.get("parts"));
            byte[] expected = hexToBytes(sample.get("digest_hex").asText());
            assertArrayEquals(expected, IdentityDigest.of(parts),
                    "target_key mismatch: " + name);
        }
    }

    @Test
    void digestLengthIs32Bytes() {
        assertEquals(32, IdentityDigest.of("any").length);
        assertEquals(32, IdentityDigest.of("a", "b", "c", "d").length);
    }

    @Test
    void emptyPartIsLegal() {
        assertTrue(IdentityDigest.of("").length == 32);
        assertArrayEquals(IdentityDigest.of(""), IdentityDigest.of(""),
                "same parts must produce the same digest");
    }

    private static String[] toArray(JsonNode node) {
        String[] parts = new String[node.size()];
        for (int i = 0; i < node.size(); i++) {
            parts[i] = node.get(i).asText();
        }
        return parts;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
