package com.abchina.llmalf.agentgate.domain.model.metric;

import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 指标聚合摘要.
 *
 * <p>对齐 Python domain/metric.py::MetricSummary:
 * 计数一致性与 overall 级别/键配对校验;level 为四值 Literal。</p>
 */
public final class MetricSummary {

    /** level wire 值:总体 */
    public static final String LEVEL_OVERALL = "overall";
    /** level wire 值:按类型 */
    public static final String LEVEL_KIND = "kind";
    /** level wire 值:按维度 */
    public static final String LEVEL_DIMENSION = "dimension";
    /** level wire 值:按指标 */
    public static final String LEVEL_METRIC = "metric";

    private final String key;
    private final String level;
    private final Double score;
    private final int passed;
    private final int failed;
    private final int reviewed;
    private final int notApplicable;
    private final int errors;
    private final int applicable;
    private final int total;

    private MetricSummary(String key, String level, Double score, int passed, int failed,
            int reviewed, int notApplicable, int errors, int applicable, int total) {
        this.key = key;
        this.level = level;
        this.score = score;
        this.passed = passed;
        this.failed = failed;
        this.reviewed = reviewed;
        this.notApplicable = notApplicable;
        this.errors = errors;
        this.applicable = applicable;
        this.total = total;
    }

    /**
     * 构造聚合摘要.
     *
     * @param key 聚合键
     * @param level overall/kind/dimension/metric
     * @param score 分数 [0,1](applicable > 0 时必填)
     * @param passed 通过数
     * @param failed 失败数
     * @param reviewed 复核数
     * @param notApplicable 不适用数
     * @param errors 错误数
     * @param applicable 可适用数(= passed + failed + reviewed)
     * @param total 总数(= 全部计数之和)
     * @return 摘要实例
     */
    public static MetricSummary of(String key, String level, Double score, int passed,
            int failed, int reviewed, int notApplicable, int errors, int applicable, int total) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("MetricSummary key must not be blank");
        }
        if (!LEVEL_OVERALL.equals(level) && !LEVEL_KIND.equals(level)
                && !LEVEL_DIMENSION.equals(level) && !LEVEL_METRIC.equals(level)) {
            throw new IllegalArgumentException(
                    "Input should be 'overall', 'kind', 'dimension' or 'metric'");
        }
        if (score != null) {
            if (score < 0) {
                throw new IllegalArgumentException("Input should be greater than or equal to 0");
            }
            if (score > 1) {
                throw new IllegalArgumentException("Input should be less than or equal to 1");
            }
        }
        int[] counts = {passed, failed, reviewed, notApplicable, errors, applicable, total};
        for (int count : counts) {
            if (count < 0) {
                throw new IllegalArgumentException("Input should be greater than or equal to 0");
            }
        }
        if (total != passed + failed + reviewed + notApplicable + errors) {
            throw new IllegalArgumentException("total must equal all outcome counts");
        }
        if (applicable != passed + failed + reviewed) {
            throw new IllegalArgumentException("applicable must equal passed + failed + reviewed");
        }
        if ((score == null) != (applicable == 0)) {
            throw new IllegalArgumentException(
                    "score must exist exactly when applicable results exist");
        }
        if (LEVEL_OVERALL.equals(level) != LEVEL_OVERALL.equals(key)) {
            throw new IllegalArgumentException("overall level and key must be used together");
        }
        return new MetricSummary(key, level, score, passed, failed, reviewed, notApplicable,
                errors, applicable, total);
    }

    /**
     * 从 payload 解析聚合摘要.
     *
     * @param payload payload 对象
     * @return 摘要实例
     */
    public static MetricSummary fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.requiredString(payload, "key"),
                PayloadValues.requiredString(payload, "level"),
                PayloadValues.optionalDouble(payload, "score"),
                intOrDefault(payload, "passed"),
                intOrDefault(payload, "failed"),
                intOrDefault(payload, "reviewed"),
                intOrDefault(payload, "not_applicable"),
                intOrDefault(payload, "errors"),
                intOrDefault(payload, "applicable"),
                intOrDefault(payload, "total"));
    }

    private static int intOrDefault(Map<String, Object> payload, String field) {
        Integer value = PayloadValues.optionalInteger(payload, field);
        return value == null ? 0 : value;
    }

    public String key() {
        return key;
    }

    public String level() {
        return level;
    }

    public Double score() {
        return score;
    }

    public int passed() {
        return passed;
    }

    public int failed() {
        return failed;
    }

    public int reviewed() {
        return reviewed;
    }

    public int notApplicable() {
        return notApplicable;
    }

    public int errors() {
        return errors;
    }

    public int applicable() {
        return applicable;
    }

    public int total() {
        return total;
    }

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", key);
        payload.put("level", level);
        payload.put("score", score);
        payload.put("passed", passed);
        payload.put("failed", failed);
        payload.put("reviewed", reviewed);
        payload.put("not_applicable", notApplicable);
        payload.put("errors", errors);
        payload.put("applicable", applicable);
        payload.put("total", total);
        return payload;
    }
}
