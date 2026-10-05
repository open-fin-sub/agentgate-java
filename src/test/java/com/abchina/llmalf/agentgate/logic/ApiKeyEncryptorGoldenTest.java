package com.abchina.llmalf.agentgate.logic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ApiKeyEncryptor 跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_api_key_encryption_golden.py 调用
 * Python agentgate.integrations.credentials.encryption 生成:
 * 8 个 (明文,密文) 对(Java 解密方向)、7 个非法密文、
 * 2 个非法主密钥、5 个非法环境值,消息逐字对齐;
 * Java 加密方向以往返 + 格式断言 + 可选样例导出覆盖。</p>
 */
class ApiKeyEncryptorGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode document;

    private static ApiKeyEncryptor encryptor;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input = ApiKeyEncryptorGoldenTest.class
                .getResourceAsStream("/contract/api-key-encryption.json")) {
            assertNotNull(input, "golden fixture /contract/api-key-encryption.json is missing");
            document = MAPPER.readTree(input);
        }
        encryptor = new ApiKeyEncryptor(hexToBytes(document.get("master_key_hex").asText()));
    }

    @Test
    void pythonCiphertextsDecryptToExpectedPlaintexts() {
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            String plaintext = sample.get("plaintext").asText();
            String encrypted = sample.get("encrypted").asText();
            assertEquals(plaintext, encryptor.decrypt(encrypted),
                    "decrypt mismatch: " + name);
        }
    }

    @Test
    void javaEncryptionRoundTripsAndMatchesWireFormat() {
        for (JsonNode sample : document.get("samples")) {
            String name = sample.get("name").asText();
            String plaintext = sample.get("plaintext").asText();
            String encrypted = encryptor.encrypt(plaintext);

            assertTrue(encrypted.startsWith("v1."), "prefix: " + name);
            assertFalse(encrypted.contains("="), "no padding: " + name);
            String body = encrypted.substring(3);
            byte[] payload = Base64.getUrlDecoder().decode(pad(body));
            assertEquals(12 + 16 + plaintext.getBytes(StandardCharsets.UTF_8).length,
                    payload.length, "payload length: " + name);
            assertEquals(plaintext, encryptor.decrypt(encrypted), "round trip: " + name);
            assertEquals(body, Base64.getUrlEncoder().withoutPadding().encodeToString(payload),
                    "canonical encoding: " + name);
        }
    }

    @Test
    void invalidCiphertextsFailWithSanitizedMessage() {
        for (JsonNode sample : document.get("invalid_ciphertexts")) {
            String name = sample.get("name").asText();
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> encryptor.decrypt(sample.get("encrypted").asText()),
                    "expected failure: " + name);
            assertEquals(sample.get("error").asText(), error.getMessage(),
                    "wrong message for " + name);
        }
    }

    @Test
    void invalidMasterKeysAreRejected() {
        for (JsonNode sample : document.get("invalid_master_keys")) {
            String name = sample.get("name").asText();
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new ApiKeyEncryptor(hexToBytes(sample.get("key_hex").asText())),
                    "expected failure: " + name);
            assertEquals(sample.get("error").asText(), error.getMessage(),
                    "wrong message for " + name);
        }
    }

    @Test
    void blankPlaintextIsRejected() {
        String expected = document.get("blank_plaintext_error").asText();
        assertEquals(expected, assertThrows(IllegalArgumentException.class,
                () -> encryptor.encrypt("")).getMessage());
        assertEquals(expected, assertThrows(IllegalArgumentException.class,
                () -> encryptor.encrypt("   ")).getMessage());
        assertEquals(expected, assertThrows(IllegalArgumentException.class,
                () -> encryptor.encrypt(null)).getMessage());
    }

    @Test
    void invalidEnvironmentValuesAreRejected() {
        for (JsonNode sample : document.get("invalid_env_values")) {
            String name = sample.get("name").asText();
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ApiKeyEncryptor.decodeMasterKey(sample.get("value").asText()),
                    "expected failure: " + name);
            assertEquals(sample.get("error").asText(), error.getMessage(),
                    "wrong message for " + name);
        }
        assertEquals("AGENTGATE_API_KEY_ENCRYPTION_KEY is required",
                assertThrows(IllegalArgumentException.class,
                        () -> ApiKeyEncryptor.fromEnvironment(null)).getMessage());
    }

    @Test
    void validEnvironmentValueRoundTrips() {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hexToBytes(document.get("master_key_hex").asText()));
        ApiKeyEncryptor fromEnv = assertDoesNotThrow(
                () -> ApiKeyEncryptor.fromEnvironment(encoded));
        assertEquals(document.get("samples").get(0).get("plaintext").asText(),
                fromEnv.decrypt(document.get("samples").get(0).get("encrypted").asText()));
    }

    @Test
    void dumpJavaEncryptedSamplesForPythonVerification() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("agentgate.dump-samples"),
                "set -Dagentgate.dump-samples=true to export Java ciphertexts");
        Map<String, Object> dump = new LinkedHashMap<>();
        dump.put("master_key_hex", document.get("master_key_hex").asText());
        List<Map<String, String>> pairs = new ArrayList<>();
        for (JsonNode sample : document.get("samples")) {
            String plaintext = sample.get("plaintext").asText();
            Map<String, String> pair = new LinkedHashMap<>();
            pair.put("plaintext", plaintext);
            pair.put("encrypted", encryptor.encrypt(plaintext));
            pairs.add(pair);
        }
        dump.put("pairs", pairs);
        Files.write(Paths.get("/tmp/api-key-java-samples.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(dump));
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static String pad(String encoded) {
        int padding = (4 - encoded.length() % 4) % 4;
        StringBuilder builder = new StringBuilder(encoded);
        for (int i = 0; i < padding; i++) {
            builder.append('=');
        }
        return builder.toString();
    }
}
