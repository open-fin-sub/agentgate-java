package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.dao.RunAssetRefDAO;
import com.abchina.llmalf.agentgate.dao.RunDAO;
import com.abchina.llmalf.agentgate.dao.TargetDescriptorDAO;
import com.abchina.llmalf.agentgate.dao.entity.RunAssetRefEntity;
import com.abchina.llmalf.agentgate.dao.entity.RunEntity;
import com.abchina.llmalf.agentgate.dao.entity.TargetDescriptorEntity;
import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.ContentSha256;
import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.IdentityDigest;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunManifest;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.domain.model.target.SkillDescriptor;
import com.abchina.llmalf.agentgate.domain.model.target.TargetDescriptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Run 域存储编排.
 *
 * <p>对齐 Python storage/mysql.py 的 Run 系列:不变量守护、资产引用
 * 计算(6 摘要主键/4 摘要查询键)、悲观锁 claim 与状态机迁移;
 * 消息文本逐字一致。</p>
 */
@Component
public class RunLogic {

    private final RunDAO runDAO;
    private final RunAssetRefDAO runAssetRefDAO;
    private final TargetDescriptorDAO targetDescriptorDAO;

    public RunLogic(RunDAO runDAO, RunAssetRefDAO runAssetRefDAO,
            TargetDescriptorDAO targetDescriptorDAO) {
        this.runDAO = runDAO;
        this.runAssetRefDAO = runAssetRefDAO;
        this.targetDescriptorDAO = targetDescriptorDAO;
    }

    /**
     * 保存 Run(新 run 连带资产引用;已有 run 走不可变守护).
     *
     * @param run Run
     */
    @Transactional
    public void saveRun(EvaluationRun run) {
        try {
            RunEntity storedEntity = runDAO.selectByIdForUpdate(IdentityDigest.of(run.id()));
            EvaluationRun stored = storedEntity == null ? null : toRun(storedEntity);
            if (stored == null) {
                insertRun(run);
                return;
            }
            if (StorageModels.samePayload(stored.toPayload(), run.toPayload())) {
                return;
            }
            if (!StorageModels.samePayload(stored.manifest().toPayload(),
                    run.manifest().toPayload())) {
                throw new IllegalArgumentException("EvaluationRun manifest is immutable");
            }
            if (!stored.createdAt().equals(run.createdAt())) {
                throw new IllegalArgumentException("EvaluationRun created_at is immutable");
            }
            if (!equalsNullable(stored.scheduledFor(), run.scheduledFor())) {
                throw new IllegalArgumentException("EvaluationRun scheduled_for is immutable");
            }
            if (stored.startedAt() != null && !stored.startedAt().equals(run.startedAt())) {
                throw new IllegalArgumentException("EvaluationRun started_at is immutable once set");
            }
            if (stored.completedAt() != null) {
                throw new IllegalArgumentException("terminal EvaluationRun is immutable");
            }
            runDAO.updateById(toEntity(run));
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 查询 Run(团队可空).
     *
     * @param runId Run id
     * @param userTeamId 团队 id(可空)
     * @return Run(可空)
     */
    @Transactional
    public EvaluationRun getRun(String runId, String userTeamId) {
        RunEntity entity = runDAO.selectByKey(IdentityDigest.of(runId));
        if (entity == null) {
            return null;
        }
        if (!entity.getId().equals(runId)) {
            throw new IllegalArgumentException("database identity digest collision");
        }
        if (userTeamId != null
                && !Arrays.equals(entity.getUserTeamKey(), IdentityDigest.of(userTeamId))) {
            return null;
        }
        return toRun(entity);
    }

    /**
     * 列出团队 Run(created_at 降序).
     *
     * @param limit 上限
     * @param userTeamId 团队 id
     * @return Run 列表
     */
    @Transactional
    public List<EvaluationRun> listRuns(int limit, String userTeamId) {
        requireLimit(limit);
        List<EvaluationRun> items = new ArrayList<>();
        for (RunEntity entity : runDAO.selectByTeamKey(IdentityDigest.of(userTeamId))) {
            if (!entity.getUserTeamId().equals(userTeamId)) {
                throw new IllegalArgumentException("database identity digest collision");
            }
            items.add(toRun(entity));
        }
        return StorageOrdering.ordered(items, EvaluationRun::createdAt, EvaluationRun::id)
                .subList(0, Math.min(limit, items.size()));
    }

    /**
     * 按状态列出 Run(调度类状态按计划时间排序).
     *
     * @param status 状态
     * @param limit 上限(可空)
     * @param oldestFirst 是否升序
     * @param userTeamId 团队 id(可空)
     * @return Run 列表
     */
    @Transactional
    public List<EvaluationRun> listRunsByStatus(RunStatus status, Integer limit,
            boolean oldestFirst, String userTeamId) {
        requireLimit(limit);
        List<EvaluationRun> items = new ArrayList<>();
        for (RunEntity entity : runDAO.selectByStatus(status.wireValue(),
                userTeamId == null ? null : IdentityDigest.of(userTeamId))) {
            if (userTeamId != null && !entity.getUserTeamId().equals(userTeamId)) {
                throw new IllegalArgumentException("database identity digest collision");
            }
            items.add(toRun(entity));
        }
        items.sort(Comparator.comparing(EvaluationRun::id));
        boolean scheduling = status == RunStatus.SCHEDULED || status == RunStatus.PENDING;
        Comparator<EvaluationRun> key = Comparator.comparing(
                run -> scheduling
                        ? (run.scheduledFor() == null ? run.createdAt() : run.scheduledFor())
                        : run.createdAt());
        items.sort(oldestFirst ? key : key.reversed());
        if (limit != null) {
            return new ArrayList<>(items.subList(0, Math.min(limit, items.size())));
        }
        return items;
    }

    /**
     * 按状态计数(全部状态含零值).
     *
     * @param userTeamId 团队 id
     * @return 状态计数
     */
    @Transactional
    public Map<RunStatus, Integer> countRunsByStatus(String userTeamId) {
        Map<RunStatus, Integer> counts = new EnumMap<>(RunStatus.class);
        for (RunStatus status : RunStatus.values()) {
            counts.put(status, 0);
        }
        for (RunEntity entity : runDAO.selectByTeamKey(IdentityDigest.of(userTeamId))) {
            if (!entity.getUserTeamId().equals(userTeamId)) {
                throw new IllegalArgumentException("database identity digest collision");
            }
            counts.put(RunStatus.fromWireValue(entity.getStatus()),
                    counts.get(RunStatus.fromWireValue(entity.getStatus())) + 1);
        }
        return counts;
    }

    /**
     * 按 API Key 计数活跃 Run(pending/running).
     *
     * @param apiKey API Key(可空)
     * @return 数量
     */
    @Transactional
    public int countActiveRunsByApiKey(String apiKey) {
        int count = 0;
        for (String status : new String[] {"pending", "running"}) {
            for (RunEntity entity : runDAO.selectByStatus(status, null)) {
                count += equalsNullable(entity.getApiKey(), apiKey) ? 1 : 0;
            }
        }
        return count;
    }

    /**
     * 认领 WAITING Run(转 PENDING).
     *
     * @param runId Run id
     * @param claimedAt 认领时间
     * @return 迁移后的 Run(不可认领为 null)
     */
    @Transactional
    public EvaluationRun claimWaitingRun(String runId, OffsetDateTime claimedAt) {
        RunEntity entity = runDAO.selectByIdForUpdate(IdentityDigest.of(runId));
        EvaluationRun run = entity == null ? null : toRun(entity);
        if (run == null || run.status() != RunStatus.WAITING) {
            return null;
        }
        EvaluationRun pending = run.transition(RunStatus.PENDING, claimedAt, null);
        runDAO.updateById(toEntity(pending));
        return pending;
    }

    /**
     * 认领 PENDING Run(转 RUNNING).
     *
     * @param runId Run id
     * @param startedAt 开始时间
     * @return 迁移后的 Run(不可认领为 null)
     */
    @Transactional
    public EvaluationRun claimPendingRun(String runId, OffsetDateTime startedAt) {
        RunEntity entity = runDAO.selectByIdForUpdate(IdentityDigest.of(runId));
        EvaluationRun run = entity == null ? null : toRun(entity);
        if (run == null || run.status() != RunStatus.PENDING) {
            return null;
        }
        EvaluationRun running = run.transition(RunStatus.RUNNING, startedAt, null);
        runDAO.updateById(toEntity(running));
        return running;
    }

    /**
     * 批量认领到期的 SCHEDULED Run(锁全量 scheduled,转 PENDING).
     *
     * @param dueAt 到期时刻
     * @param limit 上限
     * @return 迁移后的 Run 列表
     */
    @Transactional
    public List<EvaluationRun> claimDueScheduledRuns(OffsetDateTime dueAt, int limit) {
        requireLimit(limit);
        OffsetDateTime now = DomainValidations.normalizeUtc(dueAt, "scheduled Run due_at");
        List<EvaluationRun> due = new ArrayList<>();
        for (RunEntity entity : runDAO.selectByStatusForUpdate("scheduled")) {
            EvaluationRun run = toRun(entity);
            if (run.scheduledFor() != null && !run.scheduledFor().isAfter(now)) {
                due.add(run);
            }
        }
        due.sort(Comparator.comparing(EvaluationRun::scheduledFor)
                .thenComparing(EvaluationRun::createdAt)
                .thenComparing(EvaluationRun::id));
        List<EvaluationRun> claimed = new ArrayList<>(due.subList(0, Math.min(limit, due.size())));
        for (EvaluationRun run : claimed) {
            EvaluationRun pending = run.transition(RunStatus.PENDING, now, null);
            runDAO.updateById(toEntity(pending));
        }
        return claimed;
    }

    /**
     * 取消 Run(限非终态).
     *
     * @param runId Run id
     * @param cancelledAt 取消时间
     * @param userTeamId 团队 id
     * @return 迁移后的 Run(不可取消为 null)
     */
    @Transactional
    public EvaluationRun cancelRun(String runId, OffsetDateTime cancelledAt, String userTeamId) {
        RunEntity entity = runDAO.selectByIdForUpdate(IdentityDigest.of(runId));
        EvaluationRun run = entity == null ? null : toRun(entity);
        if (run == null
                || !Arrays.equals(entity.getUserTeamKey(), IdentityDigest.of(userTeamId))) {
            return null;
        }
        if (run.status() != RunStatus.PENDING && run.status() != RunStatus.SCHEDULED
                && run.status() != RunStatus.WAITING && run.status() != RunStatus.RUNNING) {
            return null;
        }
        EvaluationRun cancelled = run.transition(RunStatus.CANCELLED, cancelledAt, null);
        runDAO.updateById(toEntity(cancelled));
        return cancelled;
    }

    /**
     * 按数据集版本查 Run.
     */
    @Transactional
    public List<EvaluationRun> listRunsByDatasetVersion(String datasetId, int version, int limit,
            String userTeamId) {
        return listRunsByAsset("dataset", "", datasetId, String.valueOf(version), limit,
                null, userTeamId);
    }

    /**
     * 按用例内容查 Run.
     */
    @Transactional
    public List<EvaluationRun> listRunsByCaseContent(String datasetId, int version, String caseId,
            String contentSha256, int limit, String userTeamId) {
        return listRunsByAsset("case", datasetId, caseId, String.valueOf(version), limit,
                contentSha256, userTeamId);
    }

    /**
     * 按目标版本查 Run.
     */
    @Transactional
    public List<EvaluationRun> listRunsByTargetVersion(String sourceId, String targetTypeId,
            String targetId, String version, int limit, String contentSha256, String userTeamId) {
        return listRunsByAsset(targetTypeId, sourceId, targetId, version, limit,
                contentSha256, userTeamId);
    }

    /**
     * 按技能版本查 Run.
     */
    @Transactional
    public List<EvaluationRun> listRunsBySkillVersion(String sourceId, String skillId,
            String version, int limit, String contentSha256, String userTeamId) {
        return listRunsByAsset("skill", sourceId, skillId, version, limit,
                contentSha256, userTeamId);
    }

    /**
     * 按评测器版本查 Run.
     */
    @Transactional
    public List<EvaluationRun> listRunsByEvaluatorVersion(String evaluatorId, String version,
            int limit, String userTeamId) {
        return listRunsByAsset("evaluator", "", evaluatorId, version, limit, null, userTeamId);
    }

    private List<EvaluationRun> listRunsByAsset(String kind, String sourceId, String assetId,
            String version, int limit, String contentSha256, String userTeamId) {
        requireLimit(limit);
        byte[] lookupKey = IdentityDigest.of(kind, sourceId, assetId, version);
        Map<String, EvaluationRun> found = new LinkedHashMap<>();
        for (RunAssetRefEntity ref : runAssetRefDAO.selectByAssetLookupKey(lookupKey)) {
            if (!kind.equals(ref.getAssetKind()) || !sourceId.equals(ref.getSourceId())
                    || !assetId.equals(ref.getAssetId()) || !version.equals(ref.getVersion())) {
                throw new IllegalArgumentException("database identity digest collision");
            }
            if (contentSha256 != null && !contentSha256.equals(ref.getContentSha256())) {
                continue;
            }
            RunEntity runEntity = runDAO.selectByKey(IdentityDigest.of(ref.getRunId()));
            if (runEntity == null || !runEntity.getId().equals(ref.getRunId())) {
                if (runEntity != null) {
                    throw new IllegalArgumentException("database identity digest collision");
                }
                continue;
            }
            if (!Arrays.equals(runEntity.getUserTeamKey(), IdentityDigest.of(userTeamId))) {
                continue;
            }
            found.put(runEntity.getId(), toRun(runEntity));
        }
        return StorageOrdering.ordered(new ArrayList<>(found.values()),
                EvaluationRun::createdAt, EvaluationRun::id)
                .subList(0, Math.min(limit, found.size()));
    }

    void insertRun(EvaluationRun run) {
        runDAO.insert(toEntity(run));
        RunManifest manifest = run.manifest();
        List<String[]> refs = new ArrayList<>();
        refs.add(new String[] {"dataset", "", manifest.dataset().datasetId(),
                String.valueOf(manifest.dataset().version()),
                manifest.dataset().contentSha256()});
        for (Case item : manifest.executionCases()) {
            refs.add(new String[] {"case", manifest.dataset().datasetId(), item.id(),
                    String.valueOf(manifest.dataset().version()),
                    ContentSha256.of(item.toPayload())});
        }
        refs.add(new String[] {manifest.target().ref().targetType().wireValue(),
                manifest.target().ref().sourceId(),
                manifest.target().ref().externalTargetId(),
                manifest.target().ref().externalVersionId(),
                manifest.target().descriptorSha256()});
        TargetDescriptorEntity descriptorEntity = targetDescriptorDAO.selectById(
                manifest.target().descriptorSha256());
        if (descriptorEntity != null) {
            TargetDescriptor descriptor = TargetDescriptor.fromPayload(
                    StorageModels.parsePayload(descriptorEntity.getPayload()));
            if (!StorageModels.samePayload(descriptor.ref().toPayload(),
                    manifest.target().ref().toPayload())) {
                throw new IllegalArgumentException(
                        "TargetDescriptor reference does not match TargetSnapshot");
            }
            for (SkillDescriptor skill : descriptor.skills()) {
                refs.add(new String[] {"skill", descriptor.ref().sourceId(),
                        skill.externalSkillId(), skill.externalVersionId(),
                        ContentSha256.of(skill.toPayload())});
            }
        }
        for (int i = 0; i < manifest.evaluatorSpecs().size(); i++) {
            EvaluatorSpec spec =
                    manifest.evaluatorSpecs().get(i);
            refs.add(new String[] {"evaluator", "", spec.id(), spec.version(),
                    spec.contentSha256()});
        }
        for (String[] ref : refs) {
            RunAssetRefEntity entity = new RunAssetRefEntity();
            entity.setReferenceKey(IdentityDigest.of(run.id(), ref[0], ref[1], ref[2], ref[3],
                    ref[4]));
            entity.setRunKey(IdentityDigest.of(run.id()));
            entity.setRunId(run.id());
            entity.setAssetLookupKey(IdentityDigest.of(ref[0], ref[1], ref[2], ref[3]));
            entity.setAssetKind(ref[0]);
            entity.setSourceId(ref[1]);
            entity.setAssetId(ref[2]);
            entity.setVersion(ref[3]);
            entity.setContentSha256(ref[4]);
            runAssetRefDAO.insert(entity);
        }
    }

    private static void requireLimit(Integer limit) {
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("list limit must be at least 1");
        }
    }

    private static boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private EvaluationRun toRun(RunEntity entity) {
        EvaluationRun run = EvaluationRun.fromPayload(StorageModels.parsePayload(entity.getPayload()));
        verifyIndexed(toEntity(run), entity);
        return run;
    }

    private RunEntity toEntity(EvaluationRun run) {
        RunEntity entity = new RunEntity();
        entity.setIdKey(IdentityDigest.of(run.id()));
        entity.setId(run.id());
        entity.setStatus(run.status().wireValue());
        entity.setCreatedAt(run.createdAt().toLocalDateTime());
        entity.setScheduledFor(run.scheduledFor() == null ? null
                : run.scheduledFor().toLocalDateTime());
        entity.setUserTeamKey(IdentityDigest.of(run.userTeamId()));
        entity.setUserTeamId(run.userTeamId());
        entity.setUserId(run.userId());
        entity.setUserName(run.userName());
        entity.setApiKey(run.apiKey());
        entity.setPayload(CanonicalJson.serialize(run.toPayload()));
        return entity;
    }

    private static void verifyIndexed(RunEntity rebuilt, RunEntity stored) {
        if (!Arrays.equals(rebuilt.getIdKey(), stored.getIdKey())
                || !equalsNullable(rebuilt.getId(), stored.getId())
                || !equalsNullable(rebuilt.getStatus(), stored.getStatus())
                || !equalsNullable(rebuilt.getCreatedAt(), stored.getCreatedAt())
                || !equalsNullable(rebuilt.getScheduledFor(), stored.getScheduledFor())
                || !Arrays.equals(rebuilt.getUserTeamKey(), stored.getUserTeamKey())
                || !equalsNullable(rebuilt.getUserTeamId(), stored.getUserTeamId())
                || !equalsNullable(rebuilt.getUserId(), stored.getUserId())
                || !equalsNullable(rebuilt.getUserName(), stored.getUserName())
                || !equalsNullable(rebuilt.getApiKey(), stored.getApiKey())) {
            throw new IllegalArgumentException("stored indexed columns do not match payload");
        }
    }
}
