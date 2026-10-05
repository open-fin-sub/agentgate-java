package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.Map;

/**
 * 工具调用期望.
 *
 * <p>对齐 Python domain/expectation.py::ToolCallExpectation:
 * mode 取 required/forbidden("Input should be 'required' or 'forbidden'")。</p>
 */
public final class ToolCallExpectation extends Expectation {

    public static final String KIND = "tool_call";

    /** mode wire 值:要求调用 */
    public static final String MODE_REQUIRED = "required";
    /** mode wire 值:禁止调用 */
    public static final String MODE_FORBIDDEN = "forbidden";

    private final String tool;
    private final String mode;

    private ToolCallExpectation(String id, String name, String tool, String mode) {
        super(id, name);
        this.tool = tool;
        this.mode = mode;
    }

    /**
     * 构造工具调用期望(默认 required).
     *
     * @param id 期望 id
     * @param name 期望名(可空)
     * @param tool 工具名
     * @return 期望实例
     */
    public static ToolCallExpectation of(String id, String name, String tool) {
        return of(id, name, tool, MODE_REQUIRED);
    }

    /**
     * 构造工具调用期望.
     *
     * @param id 期望 id
     * @param name 期望名(可空)
     * @param tool 工具名
     * @param mode required 或 forbidden
     * @return 期望实例
     */
    public static ToolCallExpectation of(String id, String name, String tool, String mode) {
        if (mode == null || (!MODE_REQUIRED.equals(mode) && !MODE_FORBIDDEN.equals(mode))) {
            throw new IllegalArgumentException("Input should be 'required' or 'forbidden'");
        }
        return new ToolCallExpectation(id, name,
                DomainValidations.requireNonBlank(tool, "Tool name"), mode);
    }

    static ToolCallExpectation parse(Map<String, Object> payload) {
        return of(idOf(payload), nameOf(payload),
                PayloadValues.requiredString(payload, "tool"),
                PayloadValues.stringOrDefault(payload, "mode", MODE_REQUIRED));
    }

    public String tool() {
        return tool;
    }

    public String mode() {
        return mode;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Object toPayload() {
        Map<String, Object> payload = basePayload();
        payload.put("kind", KIND);
        payload.put("tool", tool);
        payload.put("mode", mode);
        return payload;
    }
}
