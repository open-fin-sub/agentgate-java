package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.Map;

/**
 * 输出期望.
 *
 * <p>对齐 Python domain/expectation.py::OutputExpectation:
 * path 可空,提供时非空白("Output path must not be blank")。</p>
 */
public final class OutputExpectation extends Expectation {

    public static final String KIND = "output";

    private final String path;
    private final Condition condition;

    private OutputExpectation(String id, String name, String path, Condition condition) {
        super(id, name);
        this.path = path;
        this.condition = condition;
    }

    /**
     * 构造输出期望.
     *
     * @param id 期望 id
     * @param name 期望名(可空)
     * @param path 输出路径(可空)
     * @param condition 输出条件
     * @return 期望实例
     */
    public static OutputExpectation of(String id, String name, String path, Condition condition) {
        if (path != null) {
            DomainValidations.requireNonBlank(path, "Output path");
        }
        return new OutputExpectation(id, name, path,
                requiredCondition(condition));
    }

    static OutputExpectation parse(Map<String, Object> payload) {
        return of(idOf(payload), nameOf(payload),
                PayloadValues.optionalString(payload, "path"),
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
