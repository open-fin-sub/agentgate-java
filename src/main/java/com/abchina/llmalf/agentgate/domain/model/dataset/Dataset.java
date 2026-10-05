package com.abchina.llmalf.agentgate.domain.model.dataset;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据集目录身份.
 *
 * <p>对齐 Python domain/dataset.py::Dataset:稳定的目录标识与可编辑的
 * 展示元数据;时间戳字段名无类前缀(与 EvaluationRun 系列不同)。</p>
 */
public final class Dataset {

    private final String id;
    private final String name;
    private final String description;
    private final boolean archived;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final String userTeamId;
    private final String userId;
    private final String userName;

    private Dataset(String id, String name, String description, boolean archived,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, String userTeamId,
            String userId, String userName) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.archived = archived;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.userTeamId = userTeamId;
        this.userId = userId;
        this.userName = userName;
    }

    /**
     * 构造数据集目录.
     *
     * @param id 数据集 id
     * @param name 名称
     * @param description 描述(可空)
     * @param archived 是否归档
     * @param createdAt 创建时间(可空,取当前 UTC)
     * @param updatedAt 更新时间(可空,取当前 UTC)
     * @param userTeamId 团队 id
     * @param userId 用户 id
     * @param userName 用户名
     * @return 数据集实例
     */
    public static Dataset of(String id, String name, String description, boolean archived,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, String userTeamId,
            String userId, String userName) {
        String validId = DomainValidations.requireNonBlank(id, "Dataset id");
        String validName = DomainValidations.requireNonBlank(name, "Dataset name");
        OffsetDateTime created = DomainValidations.normalizeUtc(
                createdAt == null ? DomainValidations.utcNow() : createdAt, "created_at");
        OffsetDateTime updated = DomainValidations.normalizeUtc(
                updatedAt == null ? DomainValidations.utcNow() : updatedAt, "updated_at");
        if (updated.isBefore(created)) {
            throw new IllegalArgumentException("updated_at must not precede created_at");
        }
        return new Dataset(validId, validName,
                description == null ? "" : description, archived, created, updated,
                userTeamId == null ? "" : userTeamId,
                userId == null ? "" : userId,
                userName == null ? "" : userName);
    }

    /**
     * 从 payload 解析数据集.
     *
     * @param payload payload 对象
     * @return 数据集实例
     */
    public static Dataset fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.idOrDefault(payload, "id"),
                PayloadValues.requiredString(payload, "name"),
                PayloadValues.stringOrDefault(payload, "description", ""),
                PayloadValues.boolOrDefault(payload, "archived", false),
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

    public boolean archived() {
        return archived;
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
        payload.put("archived", archived);
        payload.put("created_at", DomainValidations.isoFormat(createdAt));
        payload.put("updated_at", DomainValidations.isoFormat(updatedAt));
        payload.put("user_team_id", userTeamId);
        payload.put("user_id", userId);
        payload.put("user_name", userName);
        return payload;
    }
}
