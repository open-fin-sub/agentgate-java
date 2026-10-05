package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.Map;

/**
 * 工具参数期望.
 *
 * <p>对齐 Python domain/expectation.py::ToolArgumentExpectation:
 * occurrence 取 first/last/any/all
 * ("Input should be 'first', 'last', 'any' or 'all'"),默认 last。</p>
 */
public final class ToolArgumentExpectation extends Expectation {

    public static final String KIND = "tool_argument";

    /** occurrence wire 值 */
    public static final String OCCURRENCE_FIRST = "first";
    /** occurrence wire 值 */
    public static final String OCCURRENCE_LAST = "last";
    /** occurrence wire 值 */
    public static final String OCCURRENCE_ANY = "any";
    /** occurrence wire 值 */
    public static final String OCCURRENCE_ALL = "all";

    private final String tool;
    private final String path;
    private final String occurrence;
    private final Condition condition;

    private ToolArgumentExpectation(String id, String name, String tool, String path,
            String occurrence, Condition condition) {
        super(id, name);
        this.tool = tool;
        this.path = path;
        this.occurrence = occurrence;
        this.condition = condition;
    }

    /**
     * 构造工具参数期望.
     *
     * @param id 期望 id
     * @param name 期望名(可空)
     * @param tool 工具名
     * @param path 参数路径
     * @param occurrence first/last/any/all
     * @param condition 参数条件
     * @return 期望实例
     */
    public static ToolArgumentExpectation of(String id, String name, String tool, String path,
            String occurrence, Condition condition) {
        if (occurrence == null
                || (!OCCURRENCE_FIRST.equals(occurrence) && !OCCURRENCE_LAST.equals(occurrence)
                && !OCCURRENCE_ANY.equals(occurrence) && !OCCURRENCE_ALL.equals(occurrence))) {
            throw new IllegalArgumentException("Input should be 'first', 'last', 'any' or 'all'");
        }
        return new ToolArgumentExpectation(id, name,
                DomainValidations.requireNonBlank(tool, "Tool argument reference"),
                DomainValidations.requireNonBlank(path, "Tool argument reference"),
                occurrence,
                requiredCondition(condition));
    }

    static ToolArgumentExpectation parse(Map<String, Object> payload) {
        return of(idOf(payload), nameOf(payload),
                PayloadValues.requiredString(payload, "tool"),
                PayloadValues.requiredString(payload, "path"),
                PayloadValues.stringOrDefault(payload, "occurrence", OCCURRENCE_LAST),
                conditionOf(payload));
    }

    public String tool() {
        return tool;
    }

    public String path() {
        return path;
    }

    public String occurrence() {
        return occurrence;
    }

    public Condition condition() {
        return condition;
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
        payload.put("path", path);
        payload.put("occurrence", occurrence);
        payload.put("condition", condition.toPayload());
        return payload;
    }
}
