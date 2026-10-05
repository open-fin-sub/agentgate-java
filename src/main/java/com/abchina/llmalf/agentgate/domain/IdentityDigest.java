package com.abchina.llmalf.agentgate.domain;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 存储身份摘要.
 *
 * <p>对齐 Python storage/mysql.py::_digest:
 * sha256(canonical_json(("agentgate.mysql.identity.v1", *parts))) 的 32 字节,
 * 作为 agentgate_* 表 BINARY(32) 主键/查询键;与 Python 逐字节一致(golden 对拍项)。</p>
 */
public final class IdentityDigest {

    /** 身份摘要协议前缀(跨语言契约常量) */
    public static final String PROTOCOL_PREFIX = "agentgate.mysql.identity.v1";

    private IdentityDigest() {
    }

    /**
     * 计算身份摘要.
     *
     * @param parts 身份组成部分(单字段 id,或 TargetRef 的四元组)
     * @return 32 字节 SHA-256 摘要
     */
    public static byte[] of(String... parts) {
        List<Object> tuple = new ArrayList<>(parts.length + 1);
        tuple.add(PROTOCOL_PREFIX);
        tuple.addAll(Arrays.asList(parts));
        return ContentSha256.sha256(
                CanonicalJson.serialize(tuple).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算身份摘要的十六进制表示(调试与 golden 对拍用).
     *
     * @param parts 身份组成部分
     * @return 64 位小写十六进制
     */
    public static String ofHex(String... parts) {
        return ContentSha256.hex(of(parts));
    }
}
