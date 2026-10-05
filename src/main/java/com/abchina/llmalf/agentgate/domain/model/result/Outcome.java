package com.abchina.llmalf.agentgate.domain.model.result;

/**
 * 检查/结果结论.
 *
 * <p>对齐 Python domain/result.py::Outcome(StrEnum)。</p>
 */
public enum Outcome {

    PASS("pass"),
    FAIL("fail"),
    REVIEW("review"),
    NOT_APPLICABLE("not_applicable"),
    ERROR("error");

    private final String wireValue;

    Outcome(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写结论
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析结论.
     *
     * @param value wire 值
     * @return 枚举实例
     */
    public static Outcome fromWireValue(String value) {
        for (Outcome item : values()) {
            if (item.wireValue.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Input should be 'pass', 'fail', 'review', 'not_applicable' or 'error'");
    }
}
