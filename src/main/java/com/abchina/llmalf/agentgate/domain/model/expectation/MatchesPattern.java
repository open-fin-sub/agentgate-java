package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 正则条件.
 *
 * <p>对齐 Python domain/expectation.py::MatchesPattern 的数据面:
 * pattern 只透传存储,不做 Java 侧编译校验——Python re 与
 * java.util.regex 语法不完全兼容(如 {@code (?P<name>)}),匹配执行
 * 归属 Python 执行侧,Java 侧校验会误拒合法数据。</p>
 */
public final class MatchesPattern implements Condition {

    public static final String KIND = "matches_pattern";

    private final String pattern;

    private MatchesPattern(String pattern) {
        this.pattern = pattern;
    }

    /**
     * 构造正则条件.
     *
     * @param pattern 正则表达式文本
     * @return 条件实例
     */
    public static MatchesPattern of(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("Field required");
        }
        return new MatchesPattern(pattern);
    }

    static MatchesPattern parse(Map<String, Object> payload) {
        return of(PayloadValues.requiredString(payload, "pattern"));
    }

    public String pattern() {
        return pattern;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", KIND);
        payload.put("pattern", pattern);
        return payload;
    }
}
