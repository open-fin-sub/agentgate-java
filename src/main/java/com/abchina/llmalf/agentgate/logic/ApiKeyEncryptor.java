package com.abchina.llmalf.agentgate.logic;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * API Key 认证加密.
 *
 * <p>对齐 Python integrations/credentials/encryption.py 与
 * environment.py:AES-256-GCM(master 32 字节,AAD 固定)、
 * 12 字节随机 nonce、密文 "v1." + urlsafe-base64 无 padding;
 * 解密走严格校验链,失败统一脱敏消息(逐字一致)。</p>
 */
public final class ApiKeyEncryptor {

    /** 主密钥环境变量(与 Python .env 同名) */
    public static final String MASTER_KEY_ENV = "AGENTGATE_API_KEY_ENCRYPTION_KEY";

    private static final byte[] AAD = "agentgate:api-key:v1".getBytes(StandardCharsets.UTF_8);
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final String PREFIX = "v1.";
    private static final Pattern URLSAFE_BASE64 =
            Pattern.compile("^[A-Za-z0-9_-]+={0,2}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] masterKey;

    /**
     * 构造加密器.
     *
     * @param masterKey 主密钥(32 字节)
     */
    public ApiKeyEncryptor(byte[] masterKey) {
        if (masterKey == null || masterKey.length != 32) {
            throw new IllegalArgumentException(
                    "API Key master key must contain exactly 32 bytes");
        }
        this.masterKey = masterKey.clone();
    }

    /**
     * 加密非空白密钥.
     *
     * @param plaintext 明文
     * @return 版本化密文
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key plaintext must be a nonblank string");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        byte[] sealed;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(TAG_BYTES * 8, nonce));
            cipher.updateAAD(AAD);
            sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM unavailable", e);
        }
        byte[] payload = new byte[NONCE_BYTES + sealed.length];
        System.arraycopy(nonce, 0, payload, 0, NONCE_BYTES);
        System.arraycopy(sealed, 0, payload, NONCE_BYTES, sealed.length);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    }

    /**
     * 解密密文(严格校验链,失败统一脱敏).
     *
     * @param encrypted 密文
     * @return 明文
     */
    public String decrypt(String encrypted) {
        try {
            if (encrypted == null || !encrypted.startsWith(PREFIX)) {
                throw new IllegalArgumentException("encrypted API Key cannot be decrypted");
            }
            String encoded = encrypted.substring(PREFIX.length());
            byte[] payload = Base64.getUrlDecoder().decode(padBase64(encoded));
            String canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
            if (!canonical.equals(encoded)) {
                throw new IllegalArgumentException("encrypted API Key cannot be decrypted");
            }
            if (payload.length <= NONCE_BYTES + TAG_BYTES) {
                throw new IllegalArgumentException("encrypted API Key cannot be decrypted");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            System.arraycopy(payload, 0, nonce, 0, NONCE_BYTES);
            byte[] ciphertext = new byte[payload.length - NONCE_BYTES];
            System.arraycopy(payload, NONCE_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(TAG_BYTES * 8, nonce));
            cipher.updateAAD(AAD);
            byte[] plain = cipher.doFinal(ciphertext);
            return decodeStrictUtf8(plain);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("encrypted API Key cannot be decrypted");
        }
    }

    /**
     * 从环境变量值构造加密器.
     *
     * @param rawValue 环境变量原始值(可空)
     * @return 加密器
     */
    public static ApiKeyEncryptor fromEnvironment(String rawValue) {
        if (rawValue == null) {
            throw new IllegalArgumentException(MASTER_KEY_ENV + " is required");
        }
        return new ApiKeyEncryptor(decodeMasterKey(rawValue));
    }

    /**
     * 解码主密钥环境变量(URL-safe Base64 的 32 字节).
     *
     * @param value 环境变量值
     * @return 32 字节主密钥
     */
    public static byte[] decodeMasterKey(String value) {
        try {
            String encoded = value == null ? "" : value.trim();
            if (!URLSAFE_BASE64.matcher(encoded).matches()) {
                throw new IllegalArgumentException("bad master key encoding");
            }
            String stripped = encoded.replaceAll("=+$", "");
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(stripped));
            if (decoded.length != 32) {
                throw new IllegalArgumentException("bad master key length");
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    MASTER_KEY_ENV + " must be URL-safe Base64 for exactly 32 bytes");
        }
    }

    private static String padBase64(String encoded) {
        int padding = (4 - encoded.length() % 4) % 4;
        StringBuilder builder = new StringBuilder(encoded);
        for (int i = 0; i < padding; i++) {
            builder.append('=');
        }
        return builder.toString();
    }

    private static String decodeStrictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("encrypted API Key cannot be decrypted");
        }
    }
}
