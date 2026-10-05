package com.abchina.llmalf.agentgate.domain.model.evaluator;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 评测器目录身份.
 *
 * <p>对齐 Python domain/evaluator.py::Evaluator:
 * 内置评测器必须启用("built-in Evaluator must be enabled")。</p>
 */
public final class Evaluator {

    private final String id;
    private final String name;
    private final String description;
    private final EvaluatorSource source;
    private final boolean enabled;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final String userTeamId;
    private final String userId;
    private final String userName;

    private Evaluator(String id, String name, String description, EvaluatorSource source,
            boolean enabled, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            String userTeamId, String userId, String userName) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.source = source;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.userTeamId = userTeamId;
        this.userId = userId;
        this.userName = userName;
    }

    /**
     * 构造评测器目录.
     *
     * @param id 评测器 id
     * @param name 名称
     * @param description 描述(可空)
     * @param source 来源(默认 USER)
     * @param enabled 是否启用(默认 false)
     * @param createdAt 创建时间(可空,取当前 UTC)
     * @param updatedAt 更新时间(可空,取当前 UTC)
     * @param userTeamId 团队 id
     * @param userId 用户 id
     * @param userName 用户名
     * @return 评测器实例
     */
    public static Evaluator of(String id, String name, String description, EvaluatorSource source,
            boolean enabled, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            String userTeamId, String userId, String userName) {
        String validId = DomainValidations.requireNonBlank(id, "Evaluator id");
        String validName = DomainValidations.requireNonBlank(name, "Evaluator name");
        EvaluatorSource effectiveSource = source == null ? EvaluatorSource.USER : source;
        OffsetDateTime created = DomainValidations.normalizeUtc(
                createdAt == null ? DomainValidations.utcNow() : createdAt, "created_at");
        OffsetDateTime updated = DomainValidations.normalizeUtc(
                updatedAt == null ? DomainValidations.utcNow() : updatedAt, "updated_at");
        if (updated.isBefore(created)) {
            throw new IllegalArgumentException("updated_at must not precede created_at");
        }
        if (effectiveSource == EvaluatorSource.BUILTIN && !enabled) {
            throw new IllegalArgumentException("built-in Evaluator must be enabled");
        }
        return new Evaluator(validId, validName,
                description == null ? "" : description, effectiveSource, enabled,
                created, updated,
                userTeamId == null ? "" : userTeamId,
                userId == null ? "" : userId,
                userName == null ? "" : userName);
    }

    /**
     * 从 payload 解析评测器.
     *
     * @param payload payload 对象
     * @return 评测器实例
     */
    public static Evaluator fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.idOrDefault(payload, "id"),
                PayloadValues.requiredString(payload, "name"),
                PayloadValues.stringOrDefault(payload, "description", ""),
                EvaluatorSource.fromWireValue(PayloadValues.stringOrDefault(payload, "source",
                        EvaluatorSource.USER.wireValue())),
                PayloadValues.boolOrDefault(payload, "enabled", false),
                PayloadValues.optionalOffsetDateTime(payload, "created_at"),
                PayloadValues.optionalOffsetDateTime(payload, "updated_at"),
                PayloadValues.stringOrDefault(payload, "user_team_id", ""),
                PayloadValues.stringOrDefault(payload, "user_id", ""),
                PayloadValues.stringOrDefault(payload, "user_name", ""));
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public EvaluatorSource source() {
        return source;
    }

    public boolean enabled() {
        return enabled;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    public String userTeamId() {
        return userTeamId;
    }

    public String userId() {
        return userId;
    }

    public String userName() {
        return userName;
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
        payload.put("description", description);
        payload.put("source", source.wireValue());
        payload.put("enabled", enabled);
        payload.put("created_at", DomainValidations.isoFormat(createdAt));
        payload.put("updated_at", DomainValidations.isoFormat(updatedAt));
        payload.put("user_team_id", userTeamId);
        payload.put("user_id", userId);
        payload.put("user_name", userName);
        return payload;
    }
}
