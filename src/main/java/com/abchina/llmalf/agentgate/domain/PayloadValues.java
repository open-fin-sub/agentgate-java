package com.abchina.llmalf.agentgate.domain;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * payload 反序列化取值辅助.
 *
 * <p>对齐 pydantic 字段反序列化的错误消息文本
 * ("Field required"/"Input should be a valid string" 等),
 * 供 domain 模型 fromPayload 解析时统一使用。</p>
 */
public final class PayloadValues {

    private PayloadValues() {
    }

    /**
     * 必填字段取值(区分缺失与显式 null).
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return 字段值(可为 null)
     */
    public static Object requiredValue(Map<String, Object> payload, String field) {
        if (payload == null || !payload.containsKey(field)) {
            throw new IllegalArgumentException("Field required");
        }
        return payload.get(field);
    }

    /**
     * 必填字符串.
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return 字符串值
     */
    public static String requiredString(Map<String, Object> payload, String field) {
        Object value = requiredValue(payload, field);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Input should be a valid string");
        }
        return (String) value;
    }

    /**
     * 可选字符串(缺失或 null 返回 null).
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return 字符串值或 null
     */
    public static String optionalString(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Input should be a valid string");
        }
        return (String) value;
    }

    /**
     * 字符串取值,缺失时返回默认值.
     *
     * @param payload payload 对象
     * @param field 字段名
     * @param defaultValue 默认值
     * @return 字符串值或默认值
     */
    public static String stringOrDefault(Map<String, Object> payload, String field, String defaultValue) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Input should be a valid string");
        }
        return (String) value;
    }

    /**
     * 必填数值(整型输入宽化为 double,对齐 pydantic float 字段).
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return double 值
     */
    public static double requiredDouble(Map<String, Object> payload, String field) {
        Object value = requiredValue(payload, field);
        return toDouble(value, field);
    }

    /**
     * 可选数值(缺失或 null 返回 null).
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return Double 值或 null
     */
    public static Double optionalDouble(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null) {
            return null;
        }
        return toDouble(value, field);
    }

    /**
     * 数值取值,缺失时返回默认值.
     *
     * @param payload payload 对象
     * @param field 字段名
     * @param defaultValue 默认值
     * @return double 值或默认值
     */
    public static double doubleOrDefault(Map<String, Object> payload, String field, double defaultValue) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null) {
            return defaultValue;
        }
        return toDouble(value, field);
    }

    /**
     * 必填对象字段.
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return 子对象
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> requiredMap(Map<String, Object> payload, String field) {
        Object value = requiredValue(payload, field);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Input should be a valid dictionary");
        }
        return (Map<String, Object>) value;
    }

    /**
     * 必填数组字段.
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return 列表值
     */
    @SuppressWarnings("unchecked")
    public static List<Object> requiredList(Map<String, Object> payload, String field) {
        Object value = requiredValue(payload, field);
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        return (List<Object>) value;
    }

    /**
     * id 字段取值,缺失时生成 UUID(对齐 default_factory;显式 null 走字符串校验).
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return id 值或生成的 UUID
     */
    public static String idOrDefault(Map<String, Object> payload, String field) {
        if (payload == null || !payload.containsKey(field)) {
            return UUID.randomUUID().toString();
        }
        return requiredString(payload, field);
    }

    /**
     * 可选时间戳(缺失或 null 返回 null,非字符串或解析失败拒绝).
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return 时间值或 null
     */
    public static OffsetDateTime optionalOffsetDateTime(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Input should be a valid datetime");
        }
        try {
            return OffsetDateTime.parse((String) value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Input should be a valid datetime");
        }
    }

    /**
     * 必填整数(整型输入接受,非整数浮点拒绝).
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return int 值
     */
    public static int requiredInteger(Map<String, Object> payload, String field) {
        Integer value = optionalInteger(payload, field);
        if (value == null) {
            throw new IllegalArgumentException("Field required");
        }
        return value;
    }

    /**
     * 可选整数(整型输入接受,非整数浮点拒绝).
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return Integer 值或 null
     */
    public static Integer optionalInteger(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger) {
            return ((Number) value).intValue();
        }
        if (value instanceof Float || value instanceof Double) {
            double d = ((Number) value).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return (int) d;
            }
            throw new IllegalArgumentException(
                    "Input should be a valid integer, got a number with a fractional part");
        }
        throw new IllegalArgumentException("Input should be a valid integer");
    }

    /**
     * 布尔取值,缺失时返回默认值.
     *
     * @param payload payload 对象
     * @param field 字段名
     * @param defaultValue 默认值
     * @return 布尔值或默认值
     */
    public static boolean boolOrDefault(Map<String, Object> payload, String field, boolean defaultValue) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new IllegalArgumentException("Input should be a valid boolean");
    }

    /**
     * 必填字符串列表(元素必须是字符串).
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return 字符串列表
     */
    public static List<String> requiredStringList(Map<String, Object> payload, String field) {
        return toStringList(requiredValue(payload, field), field);
    }

    /**
     * 可选字符串列表(缺失或 null 返回 null).
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return 字符串列表或 null
     */
    public static List<String> optionalStringList(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null) {
            return null;
        }
        return toStringList(value, field);
    }

    /**
     * 可选字符串并归一(缺失/null/空串返回 null,非空白校验).
     *
     * <p>对齐 Python {@code require_non_blank(v, f) if v else None} 惯例:
     * 空字符串归一为 null。</p>
     *
     * @param payload payload 对象
     * @param field 字段名
     * @return 非空白字符串或 null
     */
    public static String optionalStringNormalized(Map<String, Object> payload, String field) {
        return normalizeOptionalText(optionalString(payload, field), field);
    }

    /**
     * 归一可选文本(空串转 null,非空白校验).
     *
     * @param value 原值
     * @param field 字段名(用于错误消息)
     * @return 非空白字符串或 null
     */
    public static String normalizeOptionalText(String value, String field) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return com.abchina.llmalf.agentgate.domain.DomainValidations.requireNonBlank(value, field);
    }

    private static List<String> toStringList(Object value, String field) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        List<String> parsed = new ArrayList<>(((List<?>) value).size());
        for (Object item : (List<?>) value) {
            if (!(item instanceof String)) {
                throw new IllegalArgumentException("Input should be a valid string");
            }
            parsed.add((String) item);
        }
        return parsed;
    }

    private static double toDouble(Object value, String field) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalArgumentException("Input should be a valid number");
    }
}
