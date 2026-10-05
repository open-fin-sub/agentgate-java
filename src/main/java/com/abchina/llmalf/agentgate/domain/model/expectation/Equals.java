package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.FrozenJson;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 等值条件.
 *
 * <p>对齐 Python domain/expectation.py::Equals:
 * expected 经 freeze_json 冻结(容器值构造期校验)。</p>
 */
public final class Equals implements Condition {

    public static final String KIND = "equals";

    private final Object expected;

    private Equals(Object expected) {
        this.expected = expected;
    }

    /**
     * 构造等值条件.
     *
     * @param expected 期望值(JSON 兼容)
     * @return 条件实例
     */
    public static Equals of(Object expected) {
        return new Equals(FrozenJson.freeze(expected));
    }

    static Equals parse(Map<String, Object> payload) {
        return of(PayloadValues.requiredValue(payload, "expected"));
    }

    public Object expected() {
        return expected;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", KIND);
        payload.put("expected", expected);
        return payload;
    }
}
