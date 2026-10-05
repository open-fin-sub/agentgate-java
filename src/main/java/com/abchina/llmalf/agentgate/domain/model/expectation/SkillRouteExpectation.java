package com.abchina.llmalf.agentgate.domain.model.expectation;

import java.util.Map;

/**
 * 技能路由期望.
 *
 * <p>对齐 Python domain/expectation.py::SkillRouteExpectation。</p>
 */
public final class SkillRouteExpectation extends Expectation {

    public static final String KIND = "skill_route";

    private final Condition condition;

    private SkillRouteExpectation(String id, String name, Condition condition) {
        super(id, name);
        this.condition = condition;
    }

    /**
     * 构造技能路由期望.
     *
     * @param id 期望 id
     * @param name 期望名(可空)
     * @param condition 路由条件
     * @return 期望实例
     */
    public static SkillRouteExpectation of(String id, String name, Condition condition) {
        return new SkillRouteExpectation(id, name, requiredCondition(condition));
    }

    static SkillRouteExpectation parse(Map<String, Object> payload) {
        return of(idOf(payload), nameOf(payload), conditionOf(payload));
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
        payload.put("condition", condition.toPayload());
        return payload;
    }
}
