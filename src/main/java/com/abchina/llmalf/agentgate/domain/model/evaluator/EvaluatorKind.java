package com.abchina.llmalf.agentgate.domain.model.evaluator;

/**
 * 评测器执行方式.
 *
 * <p>对齐 Python domain/evaluator.py::EvaluatorKind(StrEnum)。</p>
 */
public enum EvaluatorKind {

    RULE("rule"),
    LLM_JUDGE("llm_judge"),
    HYBRID("hybrid");

    private final String wireValue;

    EvaluatorKind(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写kind
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析kind.
     *
     * @param value wire 值
     * @return 枚举实例
     */
    public static EvaluatorKind fromWireValue(String value) {
        for (EvaluatorKind item : values()) {
            if (item.wireValue.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Input should be 'rule', 'llm_judge' or 'hybrid'");
    }
}
