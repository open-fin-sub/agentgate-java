package com.abchina.llmalf.agentgate.domain.model.trace;

/**
 * Span 归一化完成状态.
 *
 * <p>对齐 Python domain/trace.py::SpanStatus(StrEnum)。</p>
 */
public enum SpanStatus {

    UNSET("unset"),
    OK("ok"),
    ERROR("error");

    private final String wireValue;

    SpanStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写状态名
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析状态.
     *
     * @param value wire 值
     * @return 状态枚举
     */
    public static SpanStatus fromWireValue(String value) {
        for (SpanStatus status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Input should be 'unset', 'ok' or 'error'");
    }
}
