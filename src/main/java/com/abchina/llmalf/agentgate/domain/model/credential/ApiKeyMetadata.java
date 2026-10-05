package com.abchina.llmalf.agentgate.domain.model.credential;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API Key 无密钥身份元数据.
 *
 * <p>对齐 Python domain/credential.py::ApiKeyMetadata:
 * 密钥本体不进入领域模型;时间戳字段带类前缀归一。</p>
 */
public final class ApiKeyMetadata {

    private final String id;
    private final String name;
    private final String providerId;
    private final ApiKeyScope scope;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    private ApiKeyMetadata(String id, String name, String providerId, ApiKeyScope scope,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.providerId = providerId;
        this.scope = scope;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 构造 API Key 元数据.
     *
     * @param id Key id
     * @param name 名称
     * @param providerId 提供方 id
     * @param scope 范围(必填)
     * @param createdAt 创建时间(可空,取当前 UTC)
     * @param updatedAt 更新时间(可空,取当前 UTC)
     * @return 元数据实例
     */
    public static ApiKeyMetadata of(String id, String name, String providerId, ApiKeyScope scope,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        String validId = DomainValidations.requireNonBlank(id, "ApiKeyMetadata id");
        String validName = DomainValidations.requireNonBlank(name, "ApiKeyMetadata name");
        String validProviderId = DomainValidations.requireNonBlank(providerId,
                "ApiKeyMetadata provider_id");
        if (scope == null) {
            throw new IllegalArgumentException("Field required");
        }
        OffsetDateTime created = DomainValidations.normalizeUtc(
                createdAt == null ? DomainValidations.utcNow() : createdAt,
                "ApiKeyMetadata created_at");
        OffsetDateTime updated = DomainValidations.normalizeUtc(
                updatedAt == null ? DomainValidations.utcNow() : updatedAt,
                "ApiKeyMetadata updated_at");
        if (updated.isBefore(created)) {
            throw new IllegalArgumentException("updated_at must not precede created_at");
        }
        return new ApiKeyMetadata(validId, validName, validProviderId, scope, created, updated);
    }

    /**
     * 从 payload 解析元数据.
     *
     * @param payload payload 对象
     * @return 元数据实例
     */
    public static ApiKeyMetadata fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.idOrDefault(payload, "id"),
                PayloadValues.requiredString(payload, "name"),
                PayloadValues.requiredString(payload, "provider_id"),
                ApiKeyScope.fromWireValue(requiredScope(payload)),
                PayloadValues.optionalOffsetDateTime(payload, "created_at"),
                PayloadValues.optionalOffsetDateTime(payload, "updated_at"));
    }

    private static String requiredScope(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("scope")) {
            throw new IllegalArgumentException("Field required");
        }
        Object value = payload.get("scope");
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Input should be 'shared' or 'private'");
        }
        return (String) value;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String providerId() {
        return providerId;
    }

    public ApiKeyScope scope() {
        return scope;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
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
        payload.put("provider_id", providerId);
        payload.put("scope", scope.wireValue());
        payload.put("created_at", DomainValidations.isoFormat(createdAt));
        payload.put("updated_at", DomainValidations.isoFormat(updatedAt));
        return payload;
    }
}
