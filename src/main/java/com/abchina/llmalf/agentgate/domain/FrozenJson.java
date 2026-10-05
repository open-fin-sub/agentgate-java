package com.abchina.llmalf.agentgate.domain;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 深不可变 JSON 树.
 *
 * <p>对齐 Python domain/base.py::freeze_json/thaw_json:
 * freeze 将 Map/List 递归转换为不可变结构(构造期校验键为 String、浮点有限、
 * 类型 JSON 兼容)，thaw 深拷贝回可变的 HashMap/ArrayList。</p>
 */
public final class FrozenJson {

    private FrozenJson() {
    }

    /**
     * 深度冻结 JSON 兼容值.
     *
     * @param value JSON 兼容值
     * @return 递归不可变的 Map/List/标量
     */
    public static Object freeze(Object value) {
        if (value instanceof Map) {
            Map<String, Object> frozen = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                frozen.put((String) entry.getKey(), freeze(entry.getValue()));
            }
            return Collections.unmodifiableMap(frozen);
        }
        if (value instanceof List) {
            List<Object> frozen = new ArrayList<>();
            for (Object item : (List<?>) value) {
                frozen.add(freeze(item));
            }
            return Collections.unmodifiableList(frozen);
        }
        if (value instanceof Double) {
            double d = (Double) value;
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("JSON numbers must be finite");
            }
            return value;
        }
        if (value instanceof Float) {
            float f = (Float) value;
            if (Float.isNaN(f) || Float.isInfinite(f)) {
                throw new IllegalArgumentException("JSON numbers must be finite");
            }
            return value;
        }
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger) {
            return value;
        }
        throw new IllegalArgumentException(
                "value is not JSON-compatible: " + value.getClass().getSimpleName());
    }

    /**
     * 深拷贝为可变的 HashMap/ArrayList 结构.
     *
     * @param value 冻结或普通 JSON 兼容值
     * @return 可变深拷贝
     */
    public static Object thaw(Object value) {
        if (value instanceof Map) {
            Map<String, Object> thawed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                thawed.put((String) entry.getKey(), thaw(entry.getValue()));
            }
            return thawed;
        }
        if (value instanceof List) {
            List<Object> thawed = new ArrayList<>();
            for (Object item : (List<?>) value) {
                thawed.add(thaw(item));
            }
            return thawed;
        }
        return value;
    }
}
