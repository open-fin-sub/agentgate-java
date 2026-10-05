package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 区间条件.
 *
 * <p>对齐 Python domain/expectation.py::WithinRange:
 * 闭区间可选边界,至少一端("within_range requires minimum or maximum"),
 * 且 minimum 不超过 maximum("minimum must not exceed maximum")。</p>
 */
public final class WithinRange implements Condition {

    public static final String KIND = "within_range";

    private final Double minimum;
    private final Double maximum;

    private WithinRange(Double minimum, Double maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    /**
     * 构造区间条件(边界可空,不可双空).
     *
     * @param minimum 下界(含)
     * @param maximum 上界(含)
     * @return 条件实例
     */
    public static WithinRange of(Double minimum, Double maximum) {
        if (minimum != null && !Double.isFinite(minimum)) {
            throw new IllegalArgumentException("Input should be a finite number");
        }
        if (maximum != null && !Double.isFinite(maximum)) {
            throw new IllegalArgumentException("Input should be a finite number");
        }
        if (minimum == null && maximum == null) {
            throw new IllegalArgumentException("within_range requires minimum or maximum");
        }
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new IllegalArgumentException("minimum must not exceed maximum");
        }
        return new WithinRange(minimum, maximum);
    }

    static WithinRange parse(Map<String, Object> payload) {
        return of(PayloadValues.optionalDouble(payload, "minimum"),
                PayloadValues.optionalDouble(payload, "maximum"));
    }

    public Double minimum() {
        return minimum;
    }

    public Double maximum() {
        return maximum;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", KIND);
        payload.put("minimum", minimum);
        payload.put("maximum", maximum);
        return payload;
    }
}
