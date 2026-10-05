package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.Map;

/**
 * 状态期望.
 *
 * <p>对齐 Python domain/expectation.py::StateExpectation。</p>
 */
public final class StateExpectation extends Expectation {

    public static final String KIND = "state";

    private final String path;
    private final Condition condition;

    private StateExpectation(String id, String name, String path, Condition condition) {
        super(id, name);
        this.path = path;
        this.condition = condition;
    }

    /**
     * 构造状态期望.
     *
     * @param id 期望 id
     * @param name 期望名(可空)
     * @param path 状态路径
     * @param condition 状态条件
     * @return 期望实例
     */
    public static StateExpectation of(String id, String name, String path, Condition condition) {
        return new StateExpectation(id, name,
                DomainValidations.requireNonBlank(path, "State path"),
                requiredCondition(condition));
    }

    static StateExpectation parse(Map<String, Object> payload) {
        return of(idOf(payload), nameOf(payload),
                PayloadValues.requiredString(payload, "path"),
                conditionOf(payload));
    }

    public String path() {
        return path;
    }

    public Condition condition() {
        return condition;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Object toPayload() {
        Map<String, Object> payload = basePayload();
        payload.put("kind", KIND);
        payload.put("path", path);
        payload.put("condition", condition.toPayload());
        return payload;
    }
}
