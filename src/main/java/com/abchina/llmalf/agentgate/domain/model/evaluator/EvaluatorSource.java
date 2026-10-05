package com.abchina.llmalf.agentgate.domain.model.evaluator;

/**
 * 评测器目录变更的所有权边界.
 *
 * <p>对齐 Python domain/evaluator.py::EvaluatorSource(StrEnum)。</p>
 */
public enum EvaluatorSource {

    BUILTIN("builtin"),
    USER("user");

    private final String wireValue;

    EvaluatorSource(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写source
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析source.
     *
     * @param value wire 值
     * @return 枚举实例
     */
    public static EvaluatorSource fromWireValue(String value) {
        for (EvaluatorSource item : values()) {
            if (item.wireValue.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Input should be 'builtin' or 'user'");
    }
}
