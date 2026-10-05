package com.abchina.llmalf.agentgate.domain.model.run;

import com.abchina.llmalf.agentgate.domain.DomainValidations;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Run 生命周期状态载体.
 *
 * <p>对齐 Python domain/run.py::EvaluationRun 的生命周期字段与
 * validate_lifecycle 规则:构造期按 pydantic 字段验证器→模型验证器顺序
 * 执行全套校验,消息文本逐字一致。完整 EvaluationRun(含 manifest/身份/上下文字段)
 * 在 P1 模型就绪后另行交付,届时组合本类并拍平 payload。</p>
 */
public final class RunLifecycle {

    private final RunStatus status;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime scheduledFor;
    private final OffsetDateTime startedAt;
    private final OffsetDateTime completedAt;
    private final String error;

    private RunLifecycle(RunStatus status, OffsetDateTime createdAt, OffsetDateTime scheduledFor,
            OffsetDateTime startedAt, OffsetDateTime completedAt, String error) {
        this.status = status;
        this.createdAt = createdAt;
        this.scheduledFor = scheduledFor;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.error = error;
    }

    /**
     * 构造并校验生命周期状态.
     *
     * @param status 状态
     * @param createdAt 创建时间(必填)
     * @param scheduledFor 计划释放时间(仅 SCHEDULED 必填)
     * @param startedAt 开始时间
     * @param completedAt 完成时间
     * @param error 失败原因(仅 FAILED 允许)
     * @return 不可变生命周期状态
     */
    public static RunLifecycle of(RunStatus status, OffsetDateTime createdAt, OffsetDateTime scheduledFor,
            OffsetDateTime startedAt, OffsetDateTime completedAt, String error) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        OffsetDateTime created = DomainValidations.normalizeUtc(createdAt, "EvaluationRun created_at");
        OffsetDateTime scheduled = scheduledFor == null
                ? null : DomainValidations.normalizeUtc(scheduledFor, "EvaluationRun scheduled_for");
        OffsetDateTime started = startedAt == null
                ? null : DomainValidations.normalizeUtc(startedAt, "EvaluationRun started_at");
        OffsetDateTime completed = completedAt == null
                ? null : DomainValidations.normalizeUtc(completedAt, "EvaluationRun completed_at");

        if (error != null && DomainValidations.isBlank(error)) {
            throw new IllegalArgumentException("EvaluationRun error must not be blank");
        }
        if (scheduled != null && !scheduled.isAfter(created)) {
            throw new IllegalArgumentException("scheduled_for must be later than created_at");
        }
        if (started != null && started.isBefore(created)) {
            throw new IllegalArgumentException("started_at must not precede created_at");
        }
        if (completed != null) {
            OffsetDateTime earliest = started != null ? started : created;
            if (completed.isBefore(earliest)) {
                throw new IllegalArgumentException("completed_at must not precede Run activity");
            }
        }
        switch (status) {
            case SCHEDULED:
                if (scheduled == null) {
                    throw new IllegalArgumentException("scheduled EvaluationRun requires scheduled_for");
                }
                if (started != null || completed != null || error != null) {
                    throw new IllegalArgumentException(
                            "scheduled EvaluationRun cannot contain execution outcome");
                }
                break;
            case PENDING:
                if (started != null || completed != null || error != null) {
                    throw new IllegalArgumentException(
                            "pending EvaluationRun cannot contain execution outcome");
                }
                break;
            case WAITING:
                if (started != null || completed != null || error != null) {
                    throw new IllegalArgumentException(
                            "waiting EvaluationRun cannot contain execution outcome");
                }
                break;
            case RUNNING:
                if (started == null || completed != null || error != null) {
                    throw new IllegalArgumentException("running EvaluationRun requires only started_at");
                }
                break;
            case COMPLETED:
                if (started == null || completed == null || error != null) {
                    throw new IllegalArgumentException(
                            "completed EvaluationRun requires timestamps and no error");
                }
                break;
            case FAILED:
                if (completed == null || error == null) {
                    throw new IllegalArgumentException(
                            "failed EvaluationRun requires completed_at and error");
                }
                break;
            case CANCELLED:
                if (completed == null || error != null) {
                    throw new IllegalArgumentException(
                            "cancelled EvaluationRun requires completed_at and no error");
                }
                break;
            default:
                throw new IllegalArgumentException("unknown status: " + status);
        }
        return new RunLifecycle(status, created, scheduled, started, completed, error);
    }

    public RunStatus status() {
        return status;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime scheduledFor() {
        return scheduledFor;
    }

    public OffsetDateTime startedAt() {
        return startedAt;
    }

    public OffsetDateTime completedAt() {
        return completedAt;
    }

    public String error() {
        return error;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RunLifecycle)) {
            return false;
        }
        RunLifecycle that = (RunLifecycle) other;
        return status == that.status
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(scheduledFor, that.scheduledFor)
                && Objects.equals(startedAt, that.startedAt)
                && Objects.equals(completedAt, that.completedAt)
                && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, createdAt, scheduledFor, startedAt, completedAt, error);
    }

    @Override
    public String toString() {
        return "RunLifecycle{status=" + status.wireValue()
                + ", createdAt=" + createdAt
                + ", scheduledFor=" + scheduledFor
                + ", startedAt=" + startedAt
                + ", completedAt=" + completedAt
                + ", error=" + (error == null ? "null" : "'" + error + "'")
                + "}";
    }
}
