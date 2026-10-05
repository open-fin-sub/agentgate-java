package com.abchina.llmalf.agentgate.domain.model.credential;

/**
 * API Key 的容量与所有权范围.
 *
 * <p>对齐 Python domain/credential.py::ApiKeyScope(StrEnum)。</p>
 */
public enum ApiKeyScope {

    SHARED("shared"),
    PRIVATE("private");

    private final String wireValue;

    ApiKeyScope(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写范围名
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析范围.
     *
     * @param value wire 值
     * @return 范围枚举
     */
    public static ApiKeyScope fromWireValue(String value) {
        for (ApiKeyScope scope : values()) {
            if (scope.wireValue.equals(value)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Input should be 'shared' or 'private'");
    }
}
