package com.abchina.llmalf.agentgate.domain.model.cases;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.FrozenJson;
import com.abchina.llmalf.agentgate.domain.PayloadValues;
import com.abchina.llmalf.agentgate.domain.model.expectation.Expectation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单轮对话.
 *
 * <p>对齐 Python domain/case.py::CaseTurn:
 * id 非空白、input 冻结非空、expectations 不可变列表。</p>
 */
public final class CaseTurn {

    private final String id;
    private final Map<String, Object> input;
    private final List<Expectation> expectations;
    private final String notes;

    private CaseTurn(String id, Map<String, Object> input, List<Expectation> expectations, String notes) {
        this.id = id;
        this.input = input;
        this.expectations = expectations;
        this.notes = notes;
    }

    /**
     * 构造单轮对话(默认无期望与备注).
     *
     * @param id 轮 id
     * @param input 用户输入(冻结非空)
     * @return 轮实例
     */
    public static CaseTurn of(String id, Map<String, ?> input) {
        return of(id, input, Collections.<Expectation>emptyList(), "");
    }

    /**
     * 构造单轮对话.
     *
     * @param id 轮 id
     * @param input 用户输入(冻结非空)
     * @param expectations 期望列表(可空,视为无期望)
     * @param notes 备注(可空,视为空串)
     * @return 轮实例
     */
    public static CaseTurn of(String id, Map<String, ?> input, List<Expectation> expectations, String notes) {
        String validId = DomainValidations.requireNonBlank(id, "CaseTurn id");
        if (input == null) {
            throw new IllegalArgumentException("Field required");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> frozenInput = (Map<String, Object>) FrozenJson.freeze(input);
        if (frozenInput.isEmpty()) {
            throw new IllegalArgumentException("CaseTurn input must not be empty");
        }
        List<Expectation> frozenExpectations = expectations == null
                ? Collections.<Expectation>emptyList()
                : Collections.unmodifiableList(new ArrayList<Expectation>(expectations));
        return new CaseTurn(validId, frozenInput, frozenExpectations,
                notes == null ? "" : notes);
    }

    public static CaseTurn fromPayload(Map<String, Object> payload) {
        Object rawInput = PayloadValues.requiredValue(payload, "input");
        if (!(rawInput instanceof Map)) {
            throw new IllegalArgumentException("Input should be a valid dictionary");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) rawInput;
        List<Expectation> expectations = new ArrayList<>();
        Object rawExpectations = payload.get("expectations");
        if (rawExpectations instanceof List) {
            for (Object item : (List<?>) rawExpectations) {
                if (!(item instanceof Map)) {
                    throw new IllegalArgumentException("Input should be a valid dictionary");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> expectationPayload = (Map<String, Object>) item;
                expectations.add(Expectation.fromPayload(expectationPayload));
            }
        } else if (rawExpectations != null) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        return of(PayloadValues.idOrDefault(payload, "id"), input, expectations,
                PayloadValues.stringOrDefault(payload, "notes", ""));
    }

    public String id() {
        return id;
    }

    public Map<String, Object> input() {
        return input;
    }

    public List<Expectation> expectations() {
        return expectations;
    }

    public String notes() {
        return notes;
    }

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("input", input);
        List<Object> expectationPayloads = new ArrayList<>(expectations.size());
        for (Expectation expectation : expectations) {
            expectationPayloads.add(expectation.toPayload());
        }
        payload.put("expectations", expectationPayloads);
        payload.put("notes", notes);
        return payload;
    }
}
