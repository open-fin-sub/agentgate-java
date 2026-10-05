package com.abchina.llmalf.agentgate.domain.model.result;

/**
 * 失败定位阶段.
 *
 * <p>对齐 Python domain/result.py::FailureStage(StrEnum)。</p>
 */
public enum FailureStage {

    TASK_UNDERSTANDING("task_understanding"),
    PLANNING("planning"),
    CONTEXT_RETRIEVAL("context_retrieval"),
    ROUTING("routing"),
    TOOL_SELECTION("tool_selection"),
    TOOL_ARGUMENTS("tool_arguments"),
    TOOL_EXECUTION("tool_execution"),
    RESULT_INTERPRETATION("result_interpretation"),
    FINAL_STATE("final_state"),
    FINAL_OUTPUT("final_output");

    private final String wireValue;

    FailureStage(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * payload 序列化用的 wire 值.
     *
     * @return 小写阶段
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从 wire 值解析阶段.
     *
     * @param value wire 值
     * @return 枚举实例
     */
    public static FailureStage fromWireValue(String value) {
        for (FailureStage item : values()) {
            if (item.wireValue.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Input should be 'task_understanding', 'planning', 'context_retrieval', 'routing', 'tool_selection', 'tool_arguments', 'tool_execution', 'result_interpretation', 'final_state' or 'final_output'");
    }
}
