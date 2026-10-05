package com.abchina.llmalf.agentgate.domain.model.evaluator;

/**
 * 评测器失败是否阻断发布门禁.
 *
 * <p>对齐 Python domain/evaluator.py::EvaluatorSeverity(StrEnum)。</p>
 */
public enum EvaluatorSeverity {

    STANDARD("standard"),
    BLOCKING("blocking");

    private final String wireValue;

    EvaluatorSeverity(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写severity
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析severity.
     *
     * @param value wire 值
     * @return 枚举实例
     */
    public static EvaluatorSeverity fromWireValue(String value) {
        for (EvaluatorSeverity item : values()) {
            if (item.wireValue.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Input should be 'standard' or 'blocking'");
    }
}
