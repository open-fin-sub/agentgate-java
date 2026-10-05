package com.abchina.llmalf.agentgate.domain.model.cases;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.FrozenJson;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评测用例(单轮或多轮).
 *
 * <p>对齐 Python domain/case.py::Case:构造期校验字段约束与
 * 跨 turn 的 id 唯一性(turn id 与 expectation id)。</p>
 */
public final class Case {

    private final String id;
    private final String name;
    private final List<CaseTurn> turns;
    private final Map<String, Object> initialState;
    private final CaseCategory category;
    private final CaseDifficulty difficulty;
    private final List<String> tags;
    private final String notes;

    private Case(String id, String name, List<CaseTurn> turns, Map<String, Object> initialState,
            CaseCategory category, CaseDifficulty difficulty, List<String> tags, String notes) {
        this.id = id;
        this.name = name;
        this.turns = turns;
        this.initialState = initialState;
        this.category = category;
        this.difficulty = difficulty;
        this.tags = tags;
        this.notes = notes;
    }

    /**
     * 构造用例(默认分类/难度/标签/备注).
     *
     * @param id 用例 id
     * @param name 用例名
     * @param turns 轮列表(非空)
     * @return 用例实例
     */
    public static Case of(String id, String name, List<CaseTurn> turns) {
        return of(id, name, turns, null, CaseCategory.POSITIVE, CaseDifficulty.MEDIUM,
                null, "");
    }

    /**
     * 构造用例.
     *
     * @param id 用例 id
     * @param name 用例名
     * @param turns 轮列表(非空)
     * @param initialState 初始状态(可空,视为空)
     * @param category 分类(默认 POSITIVE)
     * @param difficulty 难度(默认 MEDIUM)
     * @param tags 标签(非空白且唯一)
     * @param notes 备注
     * @return 用例实例
     */
    public static Case of(String id, String name, List<CaseTurn> turns, Map<String, ?> initialState,
            CaseCategory category, CaseDifficulty difficulty, List<String> tags, String notes) {
        String validId = DomainValidations.requireNonBlank(id, "Case id");
        String validName = DomainValidations.requireNonBlank(name, "Case name");
        if (turns == null || turns.isEmpty()) {
            throw new IllegalArgumentException("Tuple should have at least 1 item after validation, not 0");
        }
        List<CaseTurn> frozenTurns = Collections.unmodifiableList(new ArrayList<>(turns));
        @SuppressWarnings("unchecked")
        Map<String, Object> frozenState = initialState == null
                ? Collections.<String, Object>emptyMap()
                : (Map<String, Object>) FrozenJson.freeze(initialState);
        CaseCategory effectiveCategory = category == null ? CaseCategory.POSITIVE : category;
        CaseDifficulty effectiveDifficulty = difficulty == null ? CaseDifficulty.MEDIUM : difficulty;
        List<String> frozenTags = validateTags(tags);

        Set<String> turnIds = new HashSet<>();
        for (CaseTurn turn : frozenTurns) {
            if (!turnIds.add(turn.id())) {
                throw new IllegalArgumentException("CaseTurn ids must be unique within a Case");
            }
        }
        Set<String> expectationIds = new HashSet<>();
        for (CaseTurn turn : frozenTurns) {
            for (com.abchina.llmalf.agentgate.domain.model.expectation.Expectation expectation
                    : turn.expectations()) {
                if (!expectationIds.add(expectation.id())) {
                    throw new IllegalArgumentException("Expectation ids must be unique within a Case");
                }
            }
        }
        return new Case(validId, validName, frozenTurns, frozenState,
                effectiveCategory, effectiveDifficulty, frozenTags, notes == null ? "" : notes);
    }

    public static Case fromPayload(Map<String, Object> payload) {
        List<Object> rawTurns = PayloadValues.requiredList(payload, "turns");
        List<CaseTurn> turns = new ArrayList<>(rawTurns.size());
        for (Object item : rawTurns) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Input should be a valid dictionary");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> turnPayload = (Map<String, Object>) item;
            turns.add(CaseTurn.fromPayload(turnPayload));
        }
        Map<String, Object> initialState = new LinkedHashMap<>();
        Object rawState = payload.get("initial_state");
        if (rawState instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> state = (Map<String, Object>) rawState;
            initialState = state;
        } else if (rawState != null) {
            throw new IllegalArgumentException("Input should be a valid dictionary");
        }
        List<String> tags = new ArrayList<>();
        Object rawTags = payload.get("tags");
        if (rawTags instanceof List) {
            for (Object item : (List<?>) rawTags) {
                if (!(item instanceof String)) {
                    throw new IllegalArgumentException("Input should be a valid string");
                }
                tags.add((String) item);
            }
        } else if (rawTags != null) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        return of(PayloadValues.idOrDefault(payload, "id"),
                PayloadValues.requiredString(payload, "name"),
                turns,
                initialState,
                CaseCategory.fromWireValue(PayloadValues.stringOrDefault(payload, "category",
                        CaseCategory.POSITIVE.wireValue())),
                CaseDifficulty.fromWireValue(PayloadValues.stringOrDefault(payload, "difficulty",
                        CaseDifficulty.MEDIUM.wireValue())),
                tags,
                PayloadValues.stringOrDefault(payload, "notes", ""));
    }

    private static List<String> validateTags(List<String> tags) {
        if (tags == null) {
            return Collections.emptyList();
        }
        for (String tag : tags) {
            if (DomainValidations.isBlank(tag)) {
                throw new IllegalArgumentException("tags must not contain blank values");
            }
        }
        Set<String> seen = new HashSet<>(tags);
        if (seen.size() != tags.size()) {
            throw new IllegalArgumentException("tags must not contain duplicates");
        }
        return Collections.unmodifiableList(new ArrayList<>(tags));
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<CaseTurn> turns() {
        return turns;
    }

    public Map<String, Object> initialState() {
        return initialState;
    }

    public CaseCategory category() {
        return category;
    }

    public CaseDifficulty difficulty() {
        return difficulty;
    }

    public List<String> tags() {
        return tags;
    }

    public String notes() {
        return notes;
    }

    /**
     * 是否多轮用例.
     *
     * @return turns 多于一轮时为 true
     */
    public boolean isMultiTurn() {
        return turns.size() > 1;
    }

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("name", name);
        List<Object> turnPayloads = new ArrayList<>(turns.size());
        for (CaseTurn turn : turns) {
            turnPayloads.add(turn.toPayload());
        }
        payload.put("turns", turnPayloads);
        payload.put("initial_state", initialState);
        payload.put("category", category.wireValue());
        payload.put("difficulty", difficulty.wireValue());
        payload.put("tags", tags);
        payload.put("notes", notes);
        return payload;
    }
}
