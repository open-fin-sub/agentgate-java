package com.abchina.llmalf.agentgate.domain.model.run;

import com.abchina.llmalf.agentgate.domain.DomainValidations;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Run 生命周期状态机迁移规则.
 *
 * <p>对齐 Python domain/run.py::transition_run 与 _ALLOWED_TRANSITIONS:
 * 终态(completed/failed/cancelled)无出边;迁移时校验合法性、时间顺序与
 * 各目标状态的特殊规则,经 {@link RunLifecycle#of} 全量再校验后返回新状态。</p>
 */
public final class RunTransitions {

    private static final Map<RunStatus, Set<RunStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<RunStatus, Set<RunStatus>> map = new EnumMap<>(RunStatus.class);
        map.put(RunStatus.SCHEDULED, Collections.unmodifiableSet(
                EnumSet.of(RunStatus.PENDING, RunStatus.CANCELLED)));
        map.put(RunStatus.PENDING, Collections.unmodifiableSet(
                EnumSet.of(RunStatus.WAITING, RunStatus.RUNNING, RunStatus.FAILED, RunStatus.CANCELLED)));
        map.put(RunStatus.WAITING, Collections.unmodifiableSet(
                EnumSet.of(RunStatus.PENDING, RunStatus.FAILED, RunStatus.CANCELLED)));
        map.put(RunStatus.RUNNING, Collections.unmodifiableSet(
                EnumSet.of(RunStatus.COMPLETED, RunStatus.FAILED, RunStatus.CANCELLED)));
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private RunTransitions() {
    }

    /**
     * 判定迁移是否合法.
     *
     * @param from 当前状态
     * @param to 目标状态
     * @return 合法为 true
     */
    public static boolean isLegal(RunStatus from, RunStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Collections.emptySet()).contains(to);
    }

    /**
     * 执行一次合法生命周期迁移.
     *
     * @param run 当前生命周期状态
     * @param newStatus 目标状态
     * @param occurredAt 迁移发生时间(null 取当前 UTC 时间)
     * @param error 失败原因(仅 FAILED 迁移允许)
     * @return 迁移后的新生命周期状态
     */
    public static RunLifecycle apply(RunLifecycle run, RunStatus newStatus,
            OffsetDateTime occurredAt, String error) {
        if (run == null) {
            throw new IllegalArgumentException("run must not be null");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("newStatus must not be null");
        }
        if (!isLegal(run.status(), newStatus)) {
            throw new IllegalArgumentException(
                    "illegal Run transition: " + run.status().wireValue() + " -> " + newStatus.wireValue());
        }
        OffsetDateTime timestamp = occurredAt == null
                ? DomainValidations.utcNow()
                : DomainValidations.normalizeUtc(occurredAt, "transition occurred_at");
        OffsetDateTime activity = run.startedAt() != null ? run.startedAt() : run.createdAt();
        if (timestamp.isBefore(activity)) {
            throw new IllegalArgumentException("transition occurred_at must not precede Run activity");
        }

        OffsetDateTime startedAt = run.startedAt();
        OffsetDateTime completedAt = run.completedAt();
        String nextError = null;
        if (newStatus == RunStatus.WAITING) {
            // no additional updates
        } else if (newStatus == RunStatus.PENDING) {
            if (run.status() == RunStatus.SCHEDULED
                    && (run.scheduledFor() == null || timestamp.isBefore(run.scheduledFor()))) {
                throw new IllegalArgumentException(
                        "scheduled EvaluationRun cannot be released before scheduled_for");
            }
        } else if (newStatus == RunStatus.RUNNING) {
            if (error != null) {
                throw new IllegalArgumentException("running transition cannot contain an error");
            }
            startedAt = timestamp;
        } else {
            completedAt = timestamp;
            if (newStatus == RunStatus.FAILED) {
                if (error == null || DomainValidations.isBlank(error)) {
                    throw new IllegalArgumentException("failed transition requires an error");
                }
                nextError = error;
            } else if (error != null) {
                throw new IllegalArgumentException("only failed transition may contain an error");
            }
        }
        return RunLifecycle.of(newStatus, run.createdAt(), run.scheduledFor(), startedAt, completedAt, nextError);
    }
}
