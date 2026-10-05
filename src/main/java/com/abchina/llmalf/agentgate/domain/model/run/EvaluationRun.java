package com.abchina.llmalf.agentgate.domain.model.run;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 评测 Run 完整生命周期记录.
 *
 * <p>对齐 Python domain/run.py::EvaluationRun:组合 {@link RunLifecycle}
 * 承载状态机字段(payload 拍平),manifest/api_key/dispatch_attempts 等业务字段
 * 由本类持有;transition 委托 {@link RunTransitions} 并透传不变字段。</p>
 */
public final class EvaluationRun {

    private final String id;
    private final RunManifest manifest;
    private final RunLifecycle lifecycle;
    private final String userTeamId;
    private final String userId;
    private final String userName;
    private final String apiKey;
    private final int dispatchAttempts;

    private EvaluationRun(String id, RunManifest manifest, RunLifecycle lifecycle,
            String userTeamId, String userId, String userName, String apiKey,
            int dispatchAttempts) {
        this.id = id;
        this.manifest = manifest;
        this.lifecycle = lifecycle;
        this.userTeamId = userTeamId;
        this.userId = userId;
        this.userName = userName;
        this.apiKey = apiKey;
        this.dispatchAttempts = dispatchAttempts;
    }

    /**
     * 构造评测 Run.
     *
     * @param id Run id
     * @param manifest 执行清单
     * @param lifecycle 生命周期状态(校验由 RunLifecycle 承载)
     * @param userTeamId 团队 id
     * @param userId 用户 id
     * @param userName 用户名
     * @param apiKey API Key(可空)
     * @param dispatchAttempts 派发尝试次数(非负)
     * @return Run 实例
     */
    public static EvaluationRun of(String id, RunManifest manifest, RunLifecycle lifecycle,
            String userTeamId, String userId, String userName, String apiKey,
            int dispatchAttempts) {
        String validId = DomainValidations.requireNonBlank(id, "EvaluationRun id");
        if (manifest == null) {
            throw new IllegalArgumentException("Field required");
        }
        if (lifecycle == null) {
            throw new IllegalArgumentException("Field required");
        }
        if (dispatchAttempts < 0) {
            throw new IllegalArgumentException("Input should be greater than or equal to 0");
        }
        return new EvaluationRun(validId, manifest, lifecycle,
                userTeamId == null ? "" : userTeamId,
                userId == null ? "" : userId,
                userName == null ? "" : userName,
                apiKey,
                dispatchAttempts);
    }

    /**
     * 从 payload 解析 Run.
     *
     * @param payload payload 对象
     * @return Run 实例
     */
    public static EvaluationRun fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.idOrDefault(payload, "id"),
                RunManifest.fromPayload(PayloadValues.requiredMap(payload, "manifest")),
                RunLifecycle.of(
                        RunStatus.fromWireValue(PayloadValues.stringOrDefault(payload, "status",
                                RunStatus.PENDING.wireValue())),
                        PayloadValues.optionalOffsetDateTime(payload, "created_at"),
                        PayloadValues.optionalOffsetDateTime(payload, "scheduled_for"),
                        PayloadValues.optionalOffsetDateTime(payload, "started_at"),
                        PayloadValues.optionalOffsetDateTime(payload, "completed_at"),
                        PayloadValues.optionalString(payload, "error")),
                PayloadValues.stringOrDefault(payload, "user_team_id", ""),
                PayloadValues.stringOrDefault(payload, "user_id", ""),
                PayloadValues.stringOrDefault(payload, "user_name", ""),
                PayloadValues.optionalString(payload, "api_key"),
                intOrDefault(payload, "dispatch_attempts", 0));
    }

    /**
     * 执行一次合法生命周期迁移(委托状态机,其余字段透传).
     *
     * @param newStatus 目标状态
     * @param occurredAt 迁移发生时间(null 取当前 UTC)
     * @param error 失败原因(仅 FAILED 迁移允许)
     * @return 迁移后的新 Run
     */
    public EvaluationRun transition(RunStatus newStatus, OffsetDateTime occurredAt, String error) {
        RunLifecycle next = RunTransitions.apply(lifecycle, newStatus, occurredAt, error);
        return new EvaluationRun(id, manifest, next, userTeamId, userId, userName, apiKey,
                dispatchAttempts);
    }

    /**
     * 返回仅更新派发尝试次数的副本(镜像 model_copy(update=dispatch_attempts)).
     *
     * @param nextAttempts 新尝试次数
     * @return 新 Run
     */
    public EvaluationRun withDispatchAttempts(int nextAttempts) {
        if (nextAttempts < 0) {
            throw new IllegalArgumentException("Input should be greater than or equal to 0");
        }
        return new EvaluationRun(id, manifest, lifecycle, userTeamId, userId, userName,
                apiKey, nextAttempts);
    }

    private static int intOrDefault(Map<String, Object> payload, String field, int defaultValue) {
        Integer value = PayloadValues.optionalInteger(payload, field);
        return value == null ? defaultValue : value;
    }

    public String id() {
        return id;
    }

    public RunManifest manifest() {
        return manifest;
    }

    public RunStatus status() {
        return lifecycle.status();
    }

    public OffsetDateTime createdAt() {
        return lifecycle.createdAt();
    }

    public OffsetDateTime scheduledFor() {
        return lifecycle.scheduledFor();
    }

    public OffsetDateTime startedAt() {
        return lifecycle.startedAt();
    }

    public OffsetDateTime completedAt() {
        return lifecycle.completedAt();
    }

    public String error() {
        return lifecycle.error();
    }

    public RunLifecycle lifecycle() {
        return lifecycle;
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

    public String apiKey() {
        return apiKey;
    }

    public int dispatchAttempts() {
        return dispatchAttempts;
    }

    /**
     * 组装 payload 表示(lifecycle 字段拍平,顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("manifest", manifest.toPayload());
        payload.put("status", lifecycle.status().wireValue());
        payload.put("created_at", DomainValidations.isoFormat(lifecycle.createdAt()));
        payload.put("scheduled_for", lifecycle.scheduledFor() == null
                ? null : DomainValidations.isoFormat(lifecycle.scheduledFor()));
        payload.put("started_at", lifecycle.startedAt() == null
                ? null : DomainValidations.isoFormat(lifecycle.startedAt()));
        payload.put("completed_at", lifecycle.completedAt() == null
                ? null : DomainValidations.isoFormat(lifecycle.completedAt()));
        payload.put("error", lifecycle.error());
        payload.put("user_team_id", userTeamId);
        payload.put("user_id", userId);
        payload.put("user_name", userName);
        payload.put("api_key", apiKey);
        payload.put("dispatch_attempts", dispatchAttempts);
        return payload;
    }
}
