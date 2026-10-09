package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.FrozenJson;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON Schema 条件.
 *
 * <p>对齐 Python domain/expectation.py::MatchesJsonSchema:
 * json_schema 为冻结 JSON 对象(仅类型约束,无业务校验)。</p>
 */
public final class MatchesJsonSchema implements Condition {

    public static final String KIND = "matches_json_schema";

    private final Map<String, Object> jsonSchema;

    private MatchesJsonSchema(Map<String, Object> jsonSchema) {
        this.jsonSchema = jsonSchema;
    }

    /**
     * 构造 JSON Schema 条件.
     *
     * @param jsonSchema schema 文档
     * @return 条件实例
     */
    @SuppressWarnings("unchecked")
    public static MatchesJsonSchema of(Map<String, ?> jsonSchema) {
        if (jsonSchema == null) {
            throw new IllegalArgumentException("Field required");
        }
        return new MatchesJsonSchema((Map<String, Object>) FrozenJson.freeze(jsonSchema));
    }

    static MatchesJsonSchema parse(Map<String, Object> payload) {
        return of(PayloadValues.requiredMap(payload, "json_schema"));
    }

    public Map<String, Object> jsonSchema() {
        return jsonSchema;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", KIND);
        payload.put("json_schema", jsonSchema);
        return payload;
    }
}
