package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.domain.model.evaluator.CombinationPolicy;
import com.abchina.llmalf.agentgate.domain.model.evaluator.Evaluator;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorKind;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorDraft;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorRef;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSeverity;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSource;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import java.util.List;
import java.util.Map;

/**

 * 评测器草稿发布组装.

 *

 * <p>对齐 Python evaluator/versioning.py::publish_evaluator_draft:

 * 从 evaluator 取 id/name,其余取 draft(user_team_id 亦取自 draft),

 * version 规范化为十进制字符串;EvaluatorSpec 的 content_sha256

 * 由构造期自动计算(golden 对拍项)。</p>

 */

public final class EvaluatorVersioning {

    private EvaluatorVersioning() {

    }

    /**

     * 由当前草稿组装不可变发布规格.

     *

     * @param evaluator 所属评测器(须为 user 来源)

     * @param draft 当前草稿(须属于该评测器)

     * @param version 版本号(正整数)

     * @return 发布规格

     */

    public static EvaluatorSpec publish(Evaluator evaluator, EvaluatorDraft draft, long version) {

        if (evaluator.source() != EvaluatorSource.USER) {

            throw new IllegalArgumentException(

                    "Evaluator draft operation requires a user Evaluator");

        }

        if (!draft.evaluatorId().equals(evaluator.id())) {

            throw new IllegalArgumentException("Evaluator draft belongs to another Evaluator");

        }

        if (version < 1) {

            throw new IllegalArgumentException("Evaluator version must be a positive integer");

        }

        return EvaluatorSpec.of(evaluator.id(), evaluator.name(), String.valueOf(version),

                draft.kind(), draft.dimension(), draft.metric(), draft.severity(),

                draft.implementationId(), draft.implementationVersion(), draft.config(),

                draft.children(), draft.combination(), "", draft.userTeamId(), draft.userId(),

                draft.userName());

    }

    /**

     * 批量提取发布版本号(用于版本连续性计算).

     *

     * @param versions 已发布规格列表

     * @return 最大版本号,无版本时为 0

     */

    public static long maxVersion(List<EvaluatorSpec> versions) {

        long max = 0;

        for (EvaluatorSpec spec : versions) {

            long value = parseVersion(spec.version());

            if (value > max) {

                max = value;

            }

        }

        return max;

    }

    /**

     * 规范化版本字符串为数值.

     *

     * @param value 版本字符串

     * @return 版本数值

     */

    public static long parseVersion(String value) {

        if (value == null || value.isEmpty()) {

            throw new IllegalArgumentException(

                    "Evaluator version must be a canonical positive integer");

        }

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            if (c < '0' || c > '9') {

                throw new IllegalArgumentException(

                        "Evaluator version must be a canonical positive integer");

            }

        }

        long number;

        try {

            number = Long.parseLong(value);

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(

                    "Evaluator version must be a canonical positive BIGINT");

        }

        if (number < 1 || String.valueOf(number).length() != value.length()) {

            throw new IllegalArgumentException(

                    "Evaluator version must be a canonical positive BIGINT");

        }

        return number;

    }

    /**

     * 创建无基线的完整草稿(对齐 create_evaluator_draft).

     *

     * @param evaluator 所属评测器(user 来源)

     * @param draftId 草稿 id

     * @param createdAt 创建时间

     * @param kind 执行方式

     * @param dimension 维度

     * @param metric 指标

     * @param severity 严重级

     * @param implementationId 实现 id

     * @param implementationVersion 实现版本

     * @param config 配置

     * @param children 子引用

     * @param combination 组合策略

     * @param userTeamId 团队 id

     * @param userId 用户 id

     * @param userName 用户名

     * @return 草稿

     */

    public static EvaluatorDraft createDraft(Evaluator evaluator, String draftId,

            java.time.OffsetDateTime createdAt, EvaluatorKind kind, String dimension,

            String metric, EvaluatorSeverity severity, String implementationId,

            String implementationVersion, Map<String, ?> config,

            List<EvaluatorRef> children,

            CombinationPolicy combination, String userTeamId, String userId, String userName) {

        if (evaluator.source() != EvaluatorSource.USER) {

            throw new IllegalArgumentException(

                    "Evaluator draft operation requires a user Evaluator");

        }

        if (createdAt.isBefore(evaluator.createdAt())) {

            throw new IllegalArgumentException(

                    "draft creation cannot precede Evaluator creation");

        }

        return EvaluatorDraft.of(draftId, evaluator.id(), null, kind, dimension, metric,

                severity, implementationId, implementationVersion, config, children,

                combination, createdAt, createdAt, userTeamId, userId, userName);

    }

    /**

     * 由一条精确发布版克隆草稿(对齐 clone_evaluator_version_to_draft).

     *

     * @param evaluator 所属评测器(user 来源)

     * @param baseSpec 基线发布版

     * @param draftId 草稿 id

     * @param createdAt 创建时间

     * @return 草稿

     */

    public static EvaluatorDraft cloneToDraft(Evaluator evaluator, EvaluatorSpec baseSpec,

            String draftId, java.time.OffsetDateTime createdAt) {

        if (evaluator.source() != EvaluatorSource.USER) {

            throw new IllegalArgumentException(

                    "Evaluator draft operation requires a user Evaluator");

        }

        if (!baseSpec.id().equals(evaluator.id())) {

            throw new IllegalArgumentException(

                    "Evaluator draft base belongs to another Evaluator");

        }

        return EvaluatorDraft.of(draftId, evaluator.id(), baseSpec.version(), baseSpec.kind(),

                baseSpec.dimension(), baseSpec.metric(), baseSpec.severity(),

                baseSpec.implementationId(), baseSpec.implementationVersion(),

                baseSpec.config(), baseSpec.children(), baseSpec.combination(),

                createdAt, createdAt, baseSpec.userTeamId(), baseSpec.userId(),

                baseSpec.userName());

    }

    /**

     * 整体替换草稿可编辑体(对齐 replace_evaluator_draft).

     *

     * @param draft 当前草稿

     * @param updatedAt 更新时间

     * @param kind 执行方式

     * @param dimension 维度

     * @param metric 指标

     * @param severity 严重级

     * @param implementationId 实现 id

     * @param implementationVersion 实现版本

     * @param config 配置

     * @param children 子引用

     * @param combination 组合策略

     * @return 新草稿

     */

    public static EvaluatorDraft replaceDraft(EvaluatorDraft draft,

            java.time.OffsetDateTime updatedAt, EvaluatorKind kind, String dimension,

            String metric, EvaluatorSeverity severity, String implementationId,

            String implementationVersion, Map<String, ?> config,

            List<EvaluatorRef> children,

            CombinationPolicy combination) {

        if (updatedAt.isBefore(draft.updatedAt())) {

            throw new IllegalArgumentException(

                    "updated_at must not precede the current draft update");

        }

        return EvaluatorDraft.of(draft.id(), draft.evaluatorId(), draft.basedOnVersion(),

                kind, dimension, metric, severity, implementationId, implementationVersion,

                config, children, combination, draft.createdAt(), updatedAt,

                draft.userTeamId(), draft.userId(), draft.userName());

    }

}
