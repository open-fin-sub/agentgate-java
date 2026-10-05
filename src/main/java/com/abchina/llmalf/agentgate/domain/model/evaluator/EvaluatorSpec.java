package com.abchina.llmalf.agentgate.domain.model.evaluator;

import com.abchina.llmalf.agentgate.domain.ContentSha256;
import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.FrozenJson;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测器不可变定义.
 *
 * <p>对齐 Python domain/evaluator.py::EvaluatorSpec:
 * content_sha256 由排除 content_sha256/user_* 后的全字段树计算
 * (无时间字段,hash 稳定)并自动填充/校验。</p>
 */
public final class EvaluatorSpec {

    private final String id;
    private final String name;
    private final String version;
    private final EvaluatorKind kind;
    private final String dimension;
    private final String metric;
    private final EvaluatorSeverity severity;
    private final String implementationId;
    private final String implementationVersion;
    private final Map<String, Object> config;
    private final List<EvaluatorRef> children;
    private final CombinationPolicy combination;
    private final String contentSha256;
    private final String userTeamId;
    private final String userId;
    private final String userName;

    private EvaluatorSpec(String id, String name, String version, EvaluatorKind kind,
            String dimension, String metric, EvaluatorSeverity severity, String implementationId,
            String implementationVersion, Map<String, Object> config, List<EvaluatorRef> children,
            CombinationPolicy combination, String contentSha256, String userTeamId,
            String userId, String userName) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.kind = kind;
        this.dimension = dimension;
        this.metric = metric;
        this.severity = severity;
        this.implementationId = implementationId;
        this.implementationVersion = implementationVersion;
        this.config = config;
        this.children = children;
        this.combination = combination;
        this.contentSha256 = contentSha256;
        this.userTeamId = userTeamId;
        this.userId = userId;
        this.userName = userName;
    }

    /**
     * 构造评测器定义.
     *
     * @param id 评测器 id
     * @param name 名称
     * @param version 版本(默认 "1")
     * @param kind 执行方式(默认 RULE)
     * @param dimension 评估维度
     * @param metric 指标名
     * @param severity 严重级(默认 STANDARD)
     * @param implementationId 实现 id
     * @param implementationVersion 实现版本(默认 "1")
     * @param config 配置(冻结,拒绝明文凭据)
     * @param children 子引用列表
     * @param combination 组合策略(Hybrid 必填)
     * @param contentSha256 内容摘要(空则自动计算,提供则格式校验且须一致)
     * @param userTeamId 团队 id
     * @param userId 用户 id
     * @param userName 用户名
     * @return 定义实例
     */
    public static EvaluatorSpec of(String id, String name, String version, EvaluatorKind kind,
            String dimension, String metric, EvaluatorSeverity severity, String implementationId,
            String implementationVersion, Map<String, ?> config, List<EvaluatorRef> children,
            CombinationPolicy combination, String contentSha256,
            String userTeamId, String userId, String userName) {
        String validId = DomainValidations.requireNonBlank(id, "EvaluatorSpec id");
        String validName = DomainValidations.requireNonBlank(name, "EvaluatorSpec name");
        String effectiveVersion = version == null
                ? "1" : DomainValidations.requireNonBlank(version, "EvaluatorSpec version");
        EvaluatorKind effectiveKind = kind == null ? EvaluatorKind.RULE : kind;
        String validDimension = DomainValidations.requireNonBlank(dimension,
                "EvaluatorSpec dimension");
        String validMetric = DomainValidations.requireNonBlank(metric, "EvaluatorSpec metric");
        EvaluatorSeverity effectiveSeverity = severity == null
                ? EvaluatorSeverity.STANDARD : severity;
        String validImplementationId = DomainValidations.requireNonBlank(implementationId,
                "EvaluatorSpec implementation_id");
        String effectiveImplementationVersion = implementationVersion == null
                ? "1" : DomainValidations.requireNonBlank(implementationVersion,
                        "EvaluatorSpec implementation_version");
        @SuppressWarnings("unchecked")
        Map<String, Object> frozenConfig = config == null
                ? Collections.<String, Object>emptyMap()
                : (Map<String, Object>) FrozenJson.freeze(config);
        EvaluatorDraft.rejectPlaintextCredentials(frozenConfig, "EvaluatorSpec");
        List<EvaluatorRef> frozenChildren = children == null
                ? Collections.<EvaluatorRef>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(children));
        String effectiveUserTeamId = userTeamId == null ? "" : userTeamId;
        String effectiveUserId = userId == null ? "" : userId;
        String effectiveUserName = userName == null ? "" : userName;

        EvaluatorDefinitions.validate(effectiveKind, frozenConfig, frozenChildren, combination);

        String expectedHash = ContentSha256.of(hashPayload(validId, validName, effectiveVersion,
                effectiveKind, validDimension, validMetric, effectiveSeverity, validImplementationId,
                effectiveImplementationVersion, frozenConfig, frozenChildren, combination));
        String effectiveHash;
        if (contentSha256 != null && !contentSha256.isEmpty()) {
            DomainValidations.requireSha256(contentSha256, "content_sha256");
            if (!contentSha256.equals(expectedHash)) {
                throw new IllegalArgumentException("EvaluatorSpec content hash mismatch");
            }
            effectiveHash = contentSha256;
        } else {
            effectiveHash = expectedHash;
        }

        return new EvaluatorSpec(validId, validName, effectiveVersion, effectiveKind,
                validDimension, validMetric, effectiveSeverity, validImplementationId,
                effectiveImplementationVersion, frozenConfig, frozenChildren, combination,
                effectiveHash, effectiveUserTeamId, effectiveUserId, effectiveUserName);
    }

    /**
     * 从 payload 解析定义.
     *
     * @param payload payload 对象
     * @return 定义实例
     */
    public static EvaluatorSpec fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.idOrDefault(payload, "id"),
                PayloadValues.requiredString(payload, "name"),
                PayloadValues.stringOrDefault(payload, "version", "1"),
                EvaluatorKind.fromWireValue(PayloadValues.stringOrDefault(payload, "kind",
                        EvaluatorKind.RULE.wireValue())),
                PayloadValues.requiredString(payload, "dimension"),
                PayloadValues.requiredString(payload, "metric"),
                EvaluatorSeverity.fromWireValue(PayloadValues.stringOrDefault(payload, "severity",
                        EvaluatorSeverity.STANDARD.wireValue())),
                PayloadValues.requiredString(payload, "implementation_id"),
                PayloadValues.stringOrDefault(payload, "implementation_version", "1"),
                EvaluatorDraft.parseConfig(payload),
                EvaluatorDraft.parseChildren(payload),
                EvaluatorDraft.parseCombination(payload),
                PayloadValues.stringOrDefault(payload, "content_sha256", ""),
                PayloadValues.stringOrDefault(payload, "user_team_id", ""),
                PayloadValues.stringOrDefault(payload, "user_id", ""),
                PayloadValues.stringOrDefault(payload, "user_name", ""));
    }

    private static Map<String, Object> hashPayload(String id, String name, String version,
            EvaluatorKind kind, String dimension, String metric, EvaluatorSeverity severity,
            String implementationId, String implementationVersion, Map<String, Object> config,
            List<EvaluatorRef> children, CombinationPolicy combination) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("name", name);
        payload.put("version", version);
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
        return payload;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
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

    public String contentSha256() {
        return contentSha256;
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
        payload.put("version", version);
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
        payload.put("content_sha256", contentSha256);
        payload.put("user_team_id", userTeamId);
        payload.put("user_id", userId);
        payload.put("user_name", userName);
        return payload;
    }
}
