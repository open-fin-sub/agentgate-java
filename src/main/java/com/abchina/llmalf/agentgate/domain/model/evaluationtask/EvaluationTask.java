package com.abchina.llmalf.agentgate.domain.model.evaluationtask;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评测任务(用户视角的执行与报告关联).
 *
 * <p>对齐 Python domain/evaluation_task.py::EvaluationTask:
 * 不可变关联,执行状态归属被引用的 Run;id/credential_id 共用
 * "identifier" 消息(无类前缀);基数规则按 kind 校验。</p>
 */
public final class EvaluationTask {

    private final String id;
    private final EvaluationTaskKind kind;
    private final OffsetDateTime createdAt;
    private final List<String> runIds;
    private final List<String> staticReportIds;
    private final List<String> gitCommitRefs;
    private final String credentialId;

    private EvaluationTask(String id, EvaluationTaskKind kind, OffsetDateTime createdAt,
            List<String> runIds, List<String> staticReportIds, List<String> gitCommitRefs,
            String credentialId) {
        this.id = id;
        this.kind = kind;
        this.createdAt = createdAt;
        this.runIds = runIds;
        this.staticReportIds = staticReportIds;
        this.gitCommitRefs = gitCommitRefs;
        this.credentialId = credentialId;
    }

    /**
     * 构造评测任务.
     *
     * @param id 任务 id
     * @param kind 任务类型
     * @param createdAt 创建时间(可空,取当前 UTC)
     * @param runIds Run id 列表(非空白且唯一;数量按 kind 校验)
     * @param staticReportIds 静态报告 id 列表(非空白且唯一)
     * @param gitCommitRefs Git 提交哈希列表(40/64 位十六进制)
     * @param credentialId 凭据 id(可空,非空白)
     * @return 任务实例
     */
    public static EvaluationTask of(String id, EvaluationTaskKind kind, OffsetDateTime createdAt,
            List<String> runIds, List<String> staticReportIds, List<String> gitCommitRefs,
            String credentialId) {
        String validId = DomainValidations.requireNonBlank(id, "identifier");
        if (kind == null) {
            throw new IllegalArgumentException("Field required");
        }
        OffsetDateTime created = DomainValidations.normalizeUtc(
                createdAt == null ? DomainValidations.utcNow() : createdAt, "created_at");
        List<String> frozenRunIds = validateReferences(runIds, "run_ids");
        List<String> frozenReportIds = staticReportIds == null
                ? Collections.<String>emptyList()
                : validateReferences(staticReportIds, "static_report_ids");
        List<String> frozenCommits = validateCommits(gitCommitRefs);
        if (credentialId != null) {
            DomainValidations.requireNonBlank(credentialId, "identifier");
        }

        if (kind == EvaluationTaskKind.STABILITY) {
            if (frozenRunIds.size() < 2 || frozenRunIds.size() > 20) {
                throw new IllegalArgumentException("stability requires 2 to 20 runs");
            }
            if (frozenReportIds.size() > 1) {
                throw new IllegalArgumentException("stability uses one target snapshot");
            }
            if (!frozenCommits.isEmpty()) {
                throw new IllegalArgumentException("stability Git references are not supported");
            }
            return new EvaluationTask(validId, kind, created, frozenRunIds, frozenReportIds,
                    frozenCommits, credentialId);
        }
        int count = kind == EvaluationTaskKind.SINGLE ? 1 : 2;
        if (frozenRunIds.size() != count) {
            throw new IllegalArgumentException(
                    kind.wireValue() + " requires exactly " + count + " runs");
        }
        if (frozenReportIds.size() > count) {
            throw new IllegalArgumentException("too many static reports");
        }
        if (!frozenCommits.isEmpty() && frozenCommits.size() != count) {
            throw new IllegalArgumentException("Git references must match the number of runs");
        }
        return new EvaluationTask(validId, kind, created, frozenRunIds, frozenReportIds,
                frozenCommits, credentialId);
    }

    /**
     * 从 payload 解析评测任务.
     *
     * @param payload payload 对象
     * @return 任务实例
     */
    public static EvaluationTask fromPayload(Map<String, Object> payload) {
        List<String> staticReportIds = PayloadValues.optionalStringList(payload, "static_report_ids");
        List<String> gitCommitRefs = PayloadValues.optionalStringList(payload, "git_commit_refs");
        return of(PayloadValues.idOrDefault(payload, "id"),
                EvaluationTaskKind.fromWireValue(PayloadValues.requiredString(payload, "kind")),
                PayloadValues.optionalOffsetDateTime(payload, "created_at"),
                PayloadValues.requiredStringList(payload, "run_ids"),
                staticReportIds == null ? Collections.<String>emptyList() : staticReportIds,
                gitCommitRefs == null ? Collections.<String>emptyList() : gitCommitRefs,
                PayloadValues.optionalString(payload, "credential_id"));
    }

    private static List<String> validateReferences(List<String> values, String field) {
        if (values == null) {
            throw new IllegalArgumentException("Field required");
        }
        for (String value : values) {
            if (DomainValidations.isBlank(value)) {
                throw new IllegalArgumentException("references must not be blank");
            }
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException("references must be unique");
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static List<String> validateCommits(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        for (String value : values) {
            if (value == null || !(value.matches("[0-9a-f]{40}") || value.matches("[0-9a-f]{64}"))) {
                throw new IllegalArgumentException("Git references must be full commit hashes");
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public String id() {
        return id;
    }

    public EvaluationTaskKind kind() {
        return kind;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public List<String> runIds() {
        return runIds;
    }

    public List<String> staticReportIds() {
        return staticReportIds;
    }

    public List<String> gitCommitRefs() {
        return gitCommitRefs;
    }

    public String credentialId() {
        return credentialId;
    }

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("kind", kind.wireValue());
        payload.put("created_at", DomainValidations.isoFormat(createdAt));
        payload.put("run_ids", runIds);
        payload.put("static_report_ids", staticReportIds);
        payload.put("git_commit_refs", gitCommitRefs);
        payload.put("credential_id", credentialId);
        return payload;
    }
}
