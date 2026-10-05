package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.FrozenJson;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 枚举条件.
 *
 * <p>对齐 Python domain/expectation.py::OneOf:
 * allowed 非空("Tuple should have at least 1 item after validation, not 0"),
 * 元素冻结,canonical 序列化唯一("one_of values must be unique")。</p>
 */
public final class OneOf implements Condition {

    public static final String KIND = "one_of";

    private final List<Object> allowed;

    private OneOf(List<Object> allowed) {
        this.allowed = allowed;
    }

    /**
     * 构造枚举条件.
     *
     * @param allowed 候选值列表(非空且 canonical 唯一)
     * @return 条件实例
     */
    public static OneOf of(List<?> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            throw new IllegalArgumentException("Tuple should have at least 1 item after validation, not 0");
        }
        List<Object> frozen = new ArrayList<>(allowed.size());
        for (Object item : allowed) {
            frozen.add(FrozenJson.freeze(item));
        }
        Set<String> seen = new HashSet<>();
        for (Object item : frozen) {
            if (!seen.add(CanonicalJson.serialize(item))) {
                throw new IllegalArgumentException("one_of values must be unique");
            }
        }
        return new OneOf(Collections.unmodifiableList(frozen));
    }

    static OneOf parse(Map<String, Object> payload) {
        return of(PayloadValues.requiredList(payload, "allowed"));
    }

    public List<Object> allowed() {
        return allowed;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", KIND);
        payload.put("allowed", allowed);
        return payload;
    }
}
