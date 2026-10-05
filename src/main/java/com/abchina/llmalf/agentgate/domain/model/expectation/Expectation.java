package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 期望判别基类.
 *
 * <p>对齐 Python domain/expectation.py::_ExpectationBase:
 * 公共 id(默认 UUID,非空白)与 name(可空,非空白)校验;
 * 7 个子类按 kind 判别,fromPayload 分发。</p>
 */
public abstract class Expectation {

    private final String id;
    private final String name;

    protected Expectation(String id, String name) {
        this.id = DomainValidations.requireNonBlank(id, "Expectation id");
        if (name != null) {
            DomainValidations.requireNonBlank(name, "Expectation name");
        }
        this.name = name;
    }

    /**
     * 判别 wire 值.
     *
     * @return kind 字符串
     */
    public abstract String kind();

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序:id/name 在前).
     *
     * @return JSON 兼容树
     */
    public abstract Object toPayload();

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /**
     * 基类公共 payload 字段.
     *
     * @return 含 id/name 的有序 payload
     */
    protected Map<String, Object> basePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("name", name);
        return payload;
    }

    /**
     * 从 payload 解析期望(按 kind 分发).
     *
     * @param payload 期望 payload 对象
     * @return 期望实例
     */
    public static Expectation fromPayload(Map<String, Object> payload) {
        Object kind = payload == null ? null : payload.get("kind");
        switch (kind == null ? "null" : kind.toString()) {
            case SkillRouteExpectation.KIND:
                return SkillRouteExpectation.parse(payload);
            case ToolCallExpectation.KIND:
                return ToolCallExpectation.parse(payload);
            case ToolArgumentExpectation.KIND:
                return ToolArgumentExpectation.parse(payload);
            case StateExpectation.KIND:
                return StateExpectation.parse(payload);
            case OutputExpectation.KIND:
                return OutputExpectation.parse(payload);
            case PolicyExpectation.KIND:
                return PolicyExpectation.parse(payload);
            default:
                throw new IllegalArgumentException("unknown expectation kind: " + kind);
        }
    }

    /**
     * 读取 id 字段,缺失时生成 UUID(对齐 default_factory).
     *
     * @param payload payload 对象
     * @return id 值
     */
    protected static String idOf(Map<String, Object> payload) {
        return PayloadValues.idOrDefault(payload, "id");
    }

    /**
     * 读取 name 字段,缺失时为 null.
     *
     * @param payload payload 对象
     * @return name 值或 null
     */
    protected static String nameOf(Map<String, Object> payload) {
        return PayloadValues.optionalString(payload, "name");
    }

    /**
     * 校验条件字段必填.
     *
     * @param condition 条件
     * @return 条件
     */
    protected static Condition requiredCondition(Condition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("Field required");
        }
        return condition;
    }

    /**
     * 读取并解析 condition 字段.
     *
     * @param payload payload 对象
     * @return 条件实例
     */
    protected static Condition conditionOf(Map<String, Object> payload) {
        return requiredCondition(Condition.fromPayload(PayloadValues.requiredMap(payload, "condition")));
    }
}
