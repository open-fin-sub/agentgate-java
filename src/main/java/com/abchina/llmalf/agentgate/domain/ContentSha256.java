package com.abchina.llmalf.agentgate.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 内容摘要.
 *
 * <p>对齐 Python domain/base.py::content_sha256:
 * sha256(canonical_json(value) 的 UTF-8 字节)的小写 64 位十六进制摘要.</p>
 */
public final class ContentSha256 {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ContentSha256() {
    }

    /**
     * 计算值的 canonical JSON SHA-256 摘要.
     *
     * @param value JSON 兼容值
     * @return 小写 64 位十六进制摘要
     */
    public static String of(Object value) {
        return hex(sha256(CanonicalJson.serialize(value).getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String hex(byte[] bytes) {
        StringBuilder buffer = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            buffer.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        return buffer.toString();
    }
}
