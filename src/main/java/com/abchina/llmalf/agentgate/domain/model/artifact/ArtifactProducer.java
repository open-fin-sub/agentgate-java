package com.abchina.llmalf.agentgate.domain.model.artifact;

/**
 * 产物产出方.
 *
 * <p>对齐 Python domain/artifact.py::ArtifactProducer(StrEnum)。</p>
 */
public enum ArtifactProducer {

    AGENT("agent"),
    TOOL("tool"),
    HARNESS("harness");

    private final String wireValue;

    ArtifactProducer(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写产出方名
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析产出方.
     *
     * @param value wire 值
     * @return 产出方枚举
     */
    public static ArtifactProducer fromWireValue(String value) {
        for (ArtifactProducer producer : values()) {
            if (producer.wireValue.equals(value)) {
                return producer;
            }
        }
        throw new IllegalArgumentException("Input should be 'agent', 'tool' or 'harness'");
    }
}
