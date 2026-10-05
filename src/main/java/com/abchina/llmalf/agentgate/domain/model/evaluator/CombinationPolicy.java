package com.abchina.llmalf.agentgate.domain.model.evaluator;

/**
 * 组合评测器的子结果合成策略.
 *
 * <p>对齐 Python domain/evaluator.py::CombinationPolicy(StrEnum)。</p>
 */
public enum CombinationPolicy {

    ALL("all"),
    ANY("any"),
    WEIGHTED_SCORE("weighted_score");

    private final String wireValue;

    CombinationPolicy(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写策略
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析策略.
     *
     * @param value wire 值
     * @return 枚举实例
     */
    public static CombinationPolicy fromWireValue(String value) {
        for (CombinationPolicy item : values()) {
            if (item.wireValue.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Input should be 'all', 'any' or 'weighted_score'");
    }
}
