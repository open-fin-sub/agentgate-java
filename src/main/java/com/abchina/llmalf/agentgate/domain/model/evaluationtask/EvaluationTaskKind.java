package com.abchina.llmalf.agentgate.domain.model.evaluationtask;

/**
 * 评测任务类型.
 *
 * <p>对齐 Python domain/evaluation_task.py::EvaluationTaskKind(StrEnum)。</p>
 */
public enum EvaluationTaskKind {

    SINGLE("single"),
    AB("ab"),
    STABILITY("stability");

    private final String wireValue;

    EvaluationTaskKind(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写类型名
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析类型.
     *
     * @param value wire 值
     * @return 类型枚举
     */
    public static EvaluationTaskKind fromWireValue(String value) {
        for (EvaluationTaskKind kind : values()) {
            if (kind.wireValue.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Input should be 'single', 'ab' or 'stability'");
    }
}
