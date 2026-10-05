package com.abchina.llmalf.agentgate.domain.model.evaluator;

import com.abchina.llmalf.agentgate.domain.CredentialPaths;
import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.FrozenJson;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测器未发布配置.
 *
 * <p>对齐 Python domain/evaluator.py::EvaluatorDraft:
 * 结构完整但未发布,config 拒绝明文凭据。</p>
 */
public final class EvaluatorDraft {

    private final String id;
    private final String evaluatorId;
    private final String basedOnVersion;
    private final EvaluatorKind kind;
    private final String dimension;
    private final String metric;
    private final EvaluatorSeverity severity;
    private final String implementationId;
    private final String implementationVersion;
    private final Map<String, Object> config;
    private final List<EvaluatorRef> children;
    private final CombinationPolicy combination;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final String userTeamId;
    private final String userId;
    private final String userName;

    private EvaluatorDraft(String id, String evaluatorId, String basedOnVersion, EvaluatorKind kind,
            String dimension, String metric, EvaluatorSeverity severity, String implementationId,
            String implementationVersion, Map<String, Object> config, List<EvaluatorRef> children,
            CombinationPolicy combination, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            String userTeamId, String userId, String userName) {
        this.id = id;
        this.evaluatorId = evaluatorId;
        this.basedOnVersion = basedOnVersion;
        this.kind = kind;
        this.dimension = dimension;
        this.metric = metric;
        this.severity = severity;
        this.implementationId = implementationId;
        this.implementationVersion = implementationVersion;
        this.config = config;
        this.children = children;
        this.combination = combination;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.userTeamId = userTeamId;
        this.userId = userId;
        this.userName = userName;
    }

    /**
     * 构造评测器配置草稿.
     *
     * @param id 草稿 id
     * @param evaluatorId 所属评测器 id
     * @param basedOnVersion 基线版本(可空,非空白)
     * @param kind 执行方式(默认 RULE)
     * @param dimension 评估维度
     * @param metric 指标名
     * @param severity 严重级(默认 STANDARD)
     * @param implementationId 实现 id
     * @param implementationVersion 实现版本(默认 "1")
     * @param config 配置(冻结,拒绝明文凭据)
     * @param children 子引用列表
     * @param combination 组合策略(Hybrid 必填)
     * @param createdAt 创建时间(可空,取当前 UTC)
     * @param updatedAt 更新时间(可空,取当前 UTC)
     * @param userTeamId 团队 id
     * @param userId 用户 id
     * @param userName 用户名
     * @return 草稿实例
     */
    public static EvaluatorDraft of(String id, String evaluatorId, String basedOnVersion,
            EvaluatorKind kind, String dimension, String metric, EvaluatorSeverity severity,
            String implementationId, String implementationVersion, Map<String, ?> config,
            List<EvaluatorRef> children, CombinationPolicy combination,
            OffsetDateTime createdAt, OffsetDateTime updatedAt,
            String userTeamId, String userId, String userName) {
        String validId = DomainValidations.requireNonBlank(id, "EvaluatorDraft id");
        String validEvaluatorId = DomainValidations.requireNonBlank(evaluatorId,
                "EvaluatorDraft evaluator_id");
        if (basedOnVersion != null) {
            DomainValidations.requireNonBlank(basedOnVersion, "EvaluatorDraft based_on_version");
        }
        EvaluatorKind effectiveKind = kind == null ? EvaluatorKind.RULE : kind;
        String validDimension = DomainValidations.requireNonBlank(dimension,
                "EvaluatorDraft dimension");
        String validMetric = DomainValidations.requireNonBlank(metric, "EvaluatorDraft metric");
        EvaluatorSeverity effectiveSeverity = severity == null
                ? EvaluatorSeverity.STANDARD : severity;
        String validImplementationId = DomainValidations.requireNonBlank(implementationId,
                "EvaluatorDraft implementation_id");
        String effectiveImplementationVersion = implementationVersion == null
                ? "1" : DomainValidations.requireNonBlank(implementationVersion,
                        "EvaluatorDraft implementation_version");
        @SuppressWarnings("unchecked")
        Map<String, Object> frozenConfig = config == null
                ? Collections.<String, Object>emptyMap()
                : (Map<String, Object>) FrozenJson.freeze(config);
        rejectPlaintextCredentials(frozenConfig, "EvaluatorDraft");
        List<EvaluatorRef> frozenChildren = children == null
                ? Collections.<EvaluatorRef>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(children));
        OffsetDateTime created = DomainValidations.normalizeUtc(
                createdAt == null ? DomainValidations.utcNow() : createdAt, "created_at");
        OffsetDateTime updated = DomainValidations.normalizeUtc(
                updatedAt == null ? DomainValidations.utcNow() : updatedAt, "updated_at");
        if (updated.isBefore(created)) {
            throw new IllegalArgumentException("updated_at must not precede created_at");
        }
        EvaluatorDefinitions.validate(effectiveKind, frozenConfig, frozenChildren, combination);
        return new EvaluatorDraft(validId, validEvaluatorId, basedOnVersion, effectiveKind,
                validDimension, validMetric, effectiveSeverity, validImplementationId,
                effectiveImplementationVersion, frozenConfig, frozenChildren, combination,
                created, updated,
                userTeamId == null ? "" : userTeamId,
                userId == null ? "" : userId,
                userName == null ? "" : userName);
    }

    /**
     * 从 payload 解析草稿.
     *
     * @param payload payload 对象
     * @return 草稿实例
     */
    public static EvaluatorDraft fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.idOrDefault(payload, "id"),
                PayloadValues.requiredString(payload, "evaluator_id"),
                PayloadValues.optionalString(payload, "based_on_version"),
                EvaluatorKind.fromWireValue(PayloadValues.stringOrDefault(payload, "kind",
                        EvaluatorKind.RULE.wireValue())),
                PayloadValues.requiredString(payload, "dimension"),
                PayloadValues.requiredString(payload, "metric"),
                EvaluatorSeverity.fromWireValue(PayloadValues.stringOrDefault(payload, "severity",
                        EvaluatorSeverity.STANDARD.wireValue())),
                PayloadValues.requiredString(payload, "implementation_id"),
                PayloadValues.stringOrDefault(payload, "implementation_version", "1"),
                parseConfig(payload),
                parseChildren(payload),
                parseCombination(payload),
                PayloadValues.optionalOffsetDateTime(payload, "created_at"),
                PayloadValues.optionalOffsetDateTime(payload, "updated_at"),
                PayloadValues.stringOrDefault(payload, "user_team_id", ""),
                PayloadValues.stringOrDefault(payload, "user_id", ""),
                PayloadValues.stringOrDefault(payload, "user_name", ""));
    }

    static Map<String, Object> parseConfig(Map<String, Object> payload) {
        Object rawConfig = payload == null ? null : payload.get("config");
        if (rawConfig == null) {
            return Collections.emptyMap();
        }
        if (!(rawConfig instanceof Map)) {
            throw new IllegalArgumentException("Input should be a valid dictionary");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) rawConfig;
        return config;
    }

    static List<EvaluatorRef> parseChildren(Map<String, Object> payload) {
        Object rawChildren = payload == null ? null : payload.get("children");
        if (rawChildren == null) {
            return Collections.emptyList();
        }
        if (!(rawChildren instanceof List)) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        List<EvaluatorRef> parsed = new ArrayList<>(((List<?>) rawChildren).size());
        for (Object item : (List<?>) rawChildren) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Input should be a valid dictionary");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> childPayload = (Map<String, Object>) item;
            parsed.add(EvaluatorRef.fromPayload(childPayload));
        }
        return parsed;
    }

    static CombinationPolicy parseCombination(Map<String, Object> payload) {
        Object rawCombination = payload == null ? null : payload.get("combination");
        if (rawCombination == null) {
            return null;
        }
        if (!(rawCombination instanceof String)) {
            throw new IllegalArgumentException("Input should be 'all', 'any' or 'weighted_score'");
        }
        return CombinationPolicy.fromWireValue((String) rawCombination);
    }

    static void rejectPlaintextCredentials(Map<String, Object> config, String owner) {
        String path = CredentialPaths.find(config).orElse(null);
        if (path != null) {
            throw new IllegalArgumentException(owner + " config contains credential-like field: " + path);
        }
    }

    public String id() {
        return id;
    }

    public String evaluatorId() {
        return evaluatorId;
    }

    public String basedOnVersion() {
        return basedOnVersion;
    }

    public EvaluatorKind kind() {
        return kind;
    }

    public String dimension() {
        return dimension;
    }

    public String metric() {
        return metric;
    }

    public EvaluatorSeverity severity() {
        return severity;
    }

    public String implementationId() {
        return implementationId;
    }

    public String implementationVersion() {
        return implementationVersion;
    }

    public Map<String, Object> config() {
        return config;
    }

    public List<EvaluatorRef> children() {
        return children;
    }

    public CombinationPolicy combination() {
        return combination;
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
        payload.put("evaluator_id", evaluatorId);
        payload.put("based_on_version", basedOnVersion);
        payload.put("kind", kind.wireValue());
        payload.put("dimension", dimension);
        payload.put("metric", metric);
        payload.put("severity", severity.wireValue());
        payload.put("implementation_id", implementationId);
        payload.put("implementation_version", implementationVersion);
        payload.put("config", config);
        List<Object> childPayloads = new ArrayList<>(children.size());
        for (EvaluatorRef child : children) {
            childPayloads.add(child.toPayload());
        }
        payload.put("children", childPayloads);
        payload.put("combination", combination == null ? null : combination.wireValue());
        payload.put("created_at", DomainValidations.isoFormat(createdAt));
        payload.put("updated_at", DomainValidations.isoFormat(updatedAt));
        payload.put("user_team_id", userTeamId);
        payload.put("user_id", userId);
        payload.put("user_name", userName);
        return payload;
    }
}
