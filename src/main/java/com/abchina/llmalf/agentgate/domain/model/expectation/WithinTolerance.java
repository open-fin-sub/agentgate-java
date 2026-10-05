package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 容差条件.
 *
 * <p>对齐 Python domain/expectation.py::WithinTolerance:
 * expected/epsilon 必须有限("Input should be a finite number"),
 * epsilon 严格大于 0("Input should be greater than 0"),默认 1e-6。</p>
 */
public final class WithinTolerance implements Condition {

    public static final String KIND = "within_tolerance";

    /** 默认容差(协议契约常量) */
    public static final double DEFAULT_EPSILON = 1e-6;

    private final double expected;
    private final double epsilon;

    private WithinTolerance(double expected, double epsilon) {
        this.expected = expected;
        this.epsilon = epsilon;
    }

    /**
     * 构造容差条件(默认 epsilon).
     *
     * @param expected 期望值
     * @return 条件实例
     */
    public static WithinTolerance of(double expected) {
        return of(expected, DEFAULT_EPSILON);
    }

    /**
     * 构造容差条件.
     *
     * @param expected 期望值
     * @param epsilon 容差(大于 0)
     * @return 条件实例
     */
    public static WithinTolerance of(double expected, double epsilon) {
        requireFinite(expected);
        requireFinite(epsilon);
        if (epsilon <= 0) {
            throw new IllegalArgumentException("Input should be greater than 0");
        }
        return new WithinTolerance(expected, epsilon);
    }

    static WithinTolerance parse(Map<String, Object> payload) {
        return of(PayloadValues.requiredDouble(payload, "expected"),
                PayloadValues.doubleOrDefault(payload, "epsilon", DEFAULT_EPSILON));
    }

    public double expected() {
        return expected;
    }

    public double epsilon() {
        return epsilon;
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
        payload.put("epsilon", epsilon);
        return payload;
    }

    private static void requireFinite(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Input should be a finite number");
        }
    }
}
