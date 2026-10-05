package com.abchina.llmalf.agentgate.domain.model.run;

/**
 * 评测 Run 生命周期状态.
 *
 * <p>对齐 Python domain/run.py::RunStatus(StrEnum):
 * 7 个状态,wire 值为小写字符串,用于 payload 与错误消息。</p>
 */
public enum RunStatus {

    SCHEDULED("scheduled"),
    PENDING("pending"),
    WAITING("waiting"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String wireValue;

    RunStatus(String wireValue) {
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
    public static RunStatus fromWireValue(String value) {
        for (RunStatus status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown RunStatus: " + value);
    }
}
