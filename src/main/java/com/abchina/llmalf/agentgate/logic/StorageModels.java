package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/**
 * Logic 层 payload 公共件.
 *
 * <p>对齐 Python storage/mysql.py 的 payload 解析与模型相等语义
 * (canonical 序列化比较)。</p>
 */
public final class StorageModels {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StorageModels() {
    }

    /**
     * 解析存储的 canonical JSON payload.
     *
     * @param payload payload 字符串
     * @return JSON 对象树
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> parsePayload(String payload) {
        try {
            return MAPPER.readValue(payload, Map.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("stored payload is not valid JSON", e);
        }
    }

    /**
     * 模型相等判定(canonical 序列化比较,对齐 pydantic 字段相等).
     *
     * @param left 左模型(toPayload 树)
     * @param right 右模型(toPayload 树)
     * @return 相等为 true
     */
    public static boolean samePayload(Object left, Object right) {
        return CanonicalJson.serialize(left).equals(CanonicalJson.serialize(right));
    }
}
