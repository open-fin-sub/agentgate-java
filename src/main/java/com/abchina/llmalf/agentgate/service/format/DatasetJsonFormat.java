package com.abchina.llmalf.agentgate.service.format;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 数据集 JSON 交换格式.
 *
 * <p>对齐 Python dataset/formats/json.py:envelope 四键校验
 * (缺失/多余/类型/版本),消息逐字一致。</p>
 */
public final class DatasetJsonFormat {

    /** 交换格式名(协议契约常量) */
    public static final String FORMAT_NAME = "agentgate.dataset";
    /** 交换格式版本 */
    public static final int FORMAT_VERSION = 1;

    private static final Set<String> ENVELOPE_KEYS = new HashSet<>(java.util.Arrays.asList(
            "format", "format_version", "dataset", "version"));

    private DatasetJsonFormat() {
    }

    /**
     * 校验并返回 envelope(输入已是对象树,对应 Python Mapping 分支).
     *
     * @param document 文档对象
     */
    public static void validateEnvelope(Map<String, Object> document) {
        if (document == null) {
            throw new IllegalArgumentException("Dataset JSON document must be an object");
        }
        Set<String> keys = document.keySet();
        Set<String> missing = new HashSet<>(ENVELOPE_KEYS);
        missing.removeAll(keys);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dataset JSON envelope is missing: " + joinSorted(missing));
        }
        Set<String> unexpected = new HashSet<>(keys);
        unexpected.removeAll(ENVELOPE_KEYS);
        if (!unexpected.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dataset JSON envelope has unexpected fields: " + joinSorted(unexpected));
        }
        if (!FORMAT_NAME.equals(document.get("format"))) {
            throw new IllegalArgumentException(
                    "unsupported Dataset format: '" + document.get("format") + "'");
        }
        Object version = document.get("format_version");
        if (!(version instanceof Integer)) {
            throw new IllegalArgumentException("Dataset format_version must be an integer");
        }
        if (((Integer) version).intValue() != FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported Dataset format version: " + version);
        }
        if (!(document.get("dataset") instanceof Map)) {
            throw new IllegalArgumentException(
                    "Dataset JSON envelope field 'dataset' must be an object");
        }
        if (!(document.get("version") instanceof Map)) {
            throw new IllegalArgumentException(
                    "Dataset JSON envelope field 'version' must be an object");
        }
    }

    private static String joinSorted(Set<String> values) {
        java.util.List<String> sorted = new java.util.ArrayList<>(values);
        java.util.Collections.sort(sorted);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(sorted.get(i));
        }
        return builder.toString();
    }
}
