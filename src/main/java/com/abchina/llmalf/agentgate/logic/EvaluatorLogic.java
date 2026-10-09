package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.dao.EvaluatorDAO;
import com.abchina.llmalf.agentgate.dao.EvaluatorDraftDAO;
import com.abchina.llmalf.agentgate.dao.EvaluatorVersionDAO;
import com.abchina.llmalf.agentgate.dao.entity.EvaluatorDraftEntity;
import com.abchina.llmalf.agentgate.dao.entity.EvaluatorEntity;
import com.abchina.llmalf.agentgate.dao.entity.EvaluatorVersionEntity;
import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.IdentityDigest;
import com.abchina.llmalf.agentgate.domain.model.evaluator.Evaluator;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorDraft;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSource;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 评测器域存储编排.
 *
 * <p>对齐 Python storage/mysql.py 的 Evaluator/EvaluatorDraft/
 * EvaluatorVersion 系列:仅 user 来源可持久化、不可变字段与 stale 检查、
 * 发布版本连续性校验、payload 一致性守护;消息文本逐字一致。</p>
 */
@Component
public class EvaluatorLogic {

    private final EvaluatorDAO evaluatorDAO;
    private final EvaluatorDraftDAO evaluatorDraftDAO;
    private final EvaluatorVersionDAO evaluatorVersionDAO;

    public EvaluatorLogic(EvaluatorDAO evaluatorDAO, EvaluatorDraftDAO evaluatorDraftDAO,
            EvaluatorVersionDAO evaluatorVersionDAO) {
        this.evaluatorDAO = evaluatorDAO;
        this.evaluatorDraftDAO = evaluatorDraftDAO;
        this.evaluatorVersionDAO = evaluatorVersionDAO;
    }

    /**
     * 保存评测器目录(仅 user 来源;source/created_at 不可变,updated_at 不得回退).
     *
     * @param evaluator 评测器
     */
    @Transactional
    public void saveEvaluator(Evaluator evaluator) {
        if (evaluator.source() != EvaluatorSource.USER) {
            throw new IllegalArgumentException("only user Evaluators can be persisted");
        }
        try {
            EvaluatorEntity storedEntity = evaluatorDAO.selectByIdForUpdate(
                    IdentityDigest.of(evaluator.id()));
            Evaluator stored = storedEntity == null ? null : toEvaluator(storedEntity);
            if (stored != null) {
                if (!evaluator.createdAt().equals(stored.createdAt())
                        || evaluator.source() != stored.source()) {
                    throw new IllegalArgumentException(
                            "Evaluator source and created_at are immutable");
                }
                if (evaluator.updatedAt().isBefore(stored.updatedAt())) {
                    throw new IllegalArgumentException("cannot save a stale Evaluator");
                }
                evaluatorDAO.updateById(toEntity(evaluator));
            } else {
                evaluatorDAO.insert(toEntity(evaluator));
            }
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 保存评测器与其草稿(同一事务).
     *
     * @param evaluator 评测器
     * @param draft 草稿
     */
    @Transactional
    public void saveEvaluatorWithDraft(Evaluator evaluator, EvaluatorDraft draft) {
        if (evaluator.source() != EvaluatorSource.USER) {
            throw new IllegalArgumentException("only user Evaluators can be persisted");
        }
        if (!draft.evaluatorId().equals(evaluator.id())
                || draft.createdAt().isBefore(evaluator.createdAt())) {
            throw new IllegalArgumentException(
                    "EvaluatorDraft must belong to Evaluator and not precede its creation");
        }
        try {
            evaluatorDAO.insert(toEntity(evaluator));
            evaluatorDraftDAO.insert(toDraftEntity(draft));
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 查询评测器.
     *
     * @param evaluatorId 评测器 id
     * @param userTeamId 团队 id
     * @return 评测器(可空)
     */
    @Transactional
    public Evaluator getEvaluator(String evaluatorId, String userTeamId) {
        EvaluatorEntity entity = evaluatorDAO.selectByKey(IdentityDigest.of(evaluatorId));
        if (entity == null) {
            return null;
        }
        if (!entity.getId().equals(evaluatorId)) {
            throw new IllegalArgumentException("database identity digest collision");
        }
        if (!Arrays.equals(entity.getUserTeamKey(), IdentityDigest.of(userTeamId))) {
            return null;
        }
        return toEvaluator(entity);
    }

    /**
     * 列出团队评测器(updated_at 降序,id 升序).
     *
     * @param includeDisabled 是否包含禁用
     * @param userTeamId 团队 id
     * @return 评测器列表
     */
    @Transactional
    public List<Evaluator> listEvaluators(boolean includeDisabled, String userTeamId) {
        List<Evaluator> items = new ArrayList<>();
        for (EvaluatorEntity entity : evaluatorDAO.selectByTeamKey(
                IdentityDigest.of(userTeamId))) {
            if (!entity.getUserTeamId().equals(userTeamId)) {
                throw new IllegalArgumentException("database identity digest collision");
            }
            items.add(toEvaluator(entity));
        }
        List<Evaluator> visible = new ArrayList<>();
        for (Evaluator item : items) {
            if (includeDisabled || item.enabled()) {
                visible.add(item);
            }
        }
        return StorageOrdering.ordered(visible, Evaluator::updatedAt, Evaluator::id);
    }

    /**
     * 删除未发布的评测器(连带草稿;已发布不可删).
     *
     * @param evaluatorId 评测器 id
     * @param userTeamId 团队 id
     */
    @Transactional
    public void deleteUnpublishedEvaluator(String evaluatorId, String userTeamId) {
        EvaluatorEntity entity = evaluatorDAO.selectByIdForUpdate(
                IdentityDigest.of(evaluatorId));
        if (entity == null
                || !Arrays.equals(entity.getUserTeamKey(),
                        IdentityDigest.of(userTeamId))) {
            throw new IllegalArgumentException("unknown Evaluator");
        }
        if (!evaluatorVersionDAO.selectByEvaluatorKey(IdentityDigest.of(evaluatorId)).isEmpty()) {
            throw new IllegalArgumentException("published Evaluator cannot be deleted");
        }
        evaluatorDraftDAO.delete(new com.baomidou.mybatisplus.core.conditions.query
                .QueryWrapper<EvaluatorDraftEntity>().eq("evaluator_key",
                IdentityDigest.of(evaluatorId)));
        evaluatorDAO.deleteByKey(IdentityDigest.of(evaluatorId));
    }

    /**
     * 保存草稿(归属校验 + 三字段不可变 + stale 检查).
     *
     * @param draft 草稿
     */
    @Transactional
    public void saveEvaluatorDraft(EvaluatorDraft draft) {
        try {
            EvaluatorEntity evaluatorEntity = evaluatorDAO.selectByIdForUpdate(
                    IdentityDigest.of(draft.evaluatorId()));
            if (evaluatorEntity == null) {
                throw new IllegalArgumentException("unknown Evaluator");
            }
            Evaluator evaluator = toEvaluator(evaluatorEntity);
            if (draft.createdAt().isBefore(evaluator.createdAt())) {
                throw new IllegalArgumentException(
                        "EvaluatorDraft cannot precede Evaluator creation");
            }
            EvaluatorDraftEntity storedEntity = evaluatorDraftDAO.selectByIdForUpdate(
                    IdentityDigest.of(draft.id()));
            EvaluatorDraft stored = storedEntity == null ? null : toDraft(storedEntity);
            if (stored != null) {
                String immutableField = firstImmutableDraftField(stored, draft);
                if (immutableField != null) {
                    throw new IllegalArgumentException(
                            "EvaluatorDraft " + immutableField + " is immutable");
                }
                if (draft.updatedAt().isBefore(stored.updatedAt())) {
                    throw new IllegalArgumentException("cannot save a stale EvaluatorDraft");
                }
                evaluatorDraftDAO.updateById(toDraftEntity(draft));
            } else {
                evaluatorDraftDAO.insert(toDraftEntity(draft));
            }
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 查询评测器当前草稿.
     *
     * @param evaluatorId 评测器 id
     * @param userTeamId 团队 id
     * @return 草稿(可空)
     */
    @Transactional
    public EvaluatorDraft getEvaluatorDraft(String evaluatorId, String userTeamId) {
        EvaluatorDraftEntity entity = evaluatorDraftDAO.selectByEvaluatorKeyTeamKey(
                IdentityDigest.of(evaluatorId), IdentityDigest.of(userTeamId));
        if (entity == null) {
            return null;
        }
        if (!entity.getEvaluatorId().equals(evaluatorId)) {
            throw new IllegalArgumentException("database identity digest collision");
        }
        return toDraft(entity);
    }

    /**
     * 删除草稿(带期望草稿 id 防误删).
     *
     * @param evaluatorId 评测器 id
     * @param expectedDraftId 期望草稿 id
     * @param userTeamId 团队 id
     */
    @Transactional
    public void deleteEvaluatorDraft(String evaluatorId, String expectedDraftId,
            String userTeamId) {
        evaluatorDAO.selectByIdForUpdate(IdentityDigest.of(evaluatorId));
        EvaluatorDraftEntity entity = evaluatorDraftDAO.selectByIdForUpdate(
                IdentityDigest.of(expectedDraftId));
        if (entity == null || !entity.getId().equals(expectedDraftId)
                || !entity.getEvaluatorId().equals(evaluatorId)
                || !Arrays.equals(entity.getUserTeamKey(),
                        IdentityDigest.of(userTeamId))) {
            throw new IllegalArgumentException("expected Evaluator draft does not exist");
        }
        evaluatorDraftDAO.deleteByKey(IdentityDigest.of(expectedDraftId));
    }

    /**
     * 列出发布版本(版本号数值降序).
     *
     * @param evaluatorId 评测器 id
     * @param userTeamId 团队 id
     * @return 版本列表
     */
    @Transactional
    public List<EvaluatorSpec> listEvaluatorVersions(String evaluatorId, String userTeamId) {
        List<EvaluatorSpec> items = new ArrayList<>();
        for (EvaluatorVersionEntity entity : evaluatorVersionDAO.selectByEvaluatorKey(
                IdentityDigest.of(evaluatorId))) {
            if (!entity.getEvaluatorId().equals(evaluatorId)
                    || !Arrays.equals(entity.getUserTeamKey(),
                            IdentityDigest.of(userTeamId))) {
                throw new IllegalArgumentException("database identity digest collision");
            }
            items.add(toVersion(entity));
        }
        items.sort((left, right) -> Long.compare(
                EvaluatorVersioning.parseVersion(right.version()),
                EvaluatorVersioning.parseVersion(left.version())));
        return items;
    }

    /**
     * 查询指定发布版本.
     *
     * @param evaluatorId 评测器 id
     * @param version 版本字符串
     * @param userTeamId 团队 id
     * @return 版本(可空)
     */
    @Transactional
    public EvaluatorSpec getEvaluatorVersion(String evaluatorId, String version,
            String userTeamId) {
        long parsed = EvaluatorVersioning.parseVersion(version);
        for (EvaluatorSpec item : listEvaluatorVersions(evaluatorId, userTeamId)) {
            if (EvaluatorVersioning.parseVersion(item.version()) == parsed) {
                return item;
            }
        }
        return null;
    }

    /**
     * 查询最新发布版本.
     *
     * @param evaluatorId 评测器 id
     * @param userTeamId 团队 id
     * @return 版本(可空)
     */
    @Transactional
    public EvaluatorSpec getLatestEvaluatorVersion(String evaluatorId, String userTeamId) {
        List<EvaluatorSpec> items = listEvaluatorVersions(evaluatorId, userTeamId);
        return items.isEmpty() ? null : items.get(0);
    }

    /**
     * 发布草稿(版本连续性 + 重组装比对,成功后删草稿).
     *
     * @param expectedDraftId 期望草稿 id
     * @param published 待发布规格
     */
    @Transactional
    public void publishEvaluatorDraft(String expectedDraftId, EvaluatorSpec published) {
        try {
            EvaluatorEntity evaluatorEntity = evaluatorDAO.selectByIdForUpdate(
                    IdentityDigest.of(published.id()));
            Evaluator evaluator = evaluatorEntity == null ? null : toEvaluator(evaluatorEntity);
            EvaluatorDraftEntity draftEntity = evaluatorDraftDAO.selectByIdForUpdate(
                    IdentityDigest.of(expectedDraftId));
            EvaluatorDraft draft = draftEntity == null ? null : toDraft(draftEntity);
            if (evaluator == null || draft == null
                    || !draft.evaluatorId().equals(evaluator.id())) {
                throw new IllegalArgumentException("expected Evaluator draft does not exist");
            }
            List<EvaluatorSpec> versions = listEvaluatorVersions(evaluator.id(),
                    evaluator.userTeamId());
            long next = EvaluatorVersioning.maxVersion(versions) + 1;
            if (EvaluatorVersioning.parseVersion(published.version()) != next) {
                throw new IllegalArgumentException(
                        "Evaluator publication requires version " + next);
            }
            EvaluatorSpec rebuilt = EvaluatorVersioning.publish(evaluator, draft, next);
            if (!StorageModels.samePayload(rebuilt.toPayload(), published.toPayload())) {
                throw new IllegalArgumentException(
                        "published EvaluatorSpec does not match the current draft");
            }
            evaluatorVersionDAO.insert(toVersionEntity(published));
            evaluatorDraftDAO.deleteByKey(IdentityDigest.of(draft.id()));
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    private static boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String firstImmutableDraftField(EvaluatorDraft stored, EvaluatorDraft draft) {
        if (!stored.evaluatorId().equals(draft.evaluatorId())) {
            return "evaluator_id";
        }
        if (!stored.createdAt().equals(draft.createdAt())) {
            return "created_at";
        }
        if (!equalsNullable(stored.basedOnVersion(), draft.basedOnVersion())) {
            return "based_on_version";
        }
        return null;
    }

    private Evaluator toEvaluator(EvaluatorEntity entity) {
        Evaluator evaluator = Evaluator.fromPayload(StorageModels.parsePayload(entity.getPayload()));
        verifyIndexed(toEntity(evaluator), entity);
        return evaluator;
    }

    private EvaluatorDraft toDraft(EvaluatorDraftEntity entity) {
        EvaluatorDraft draft = EvaluatorDraft.fromPayload(
                StorageModels.parsePayload(entity.getPayload()));
        verifyIndexed(toDraftEntity(draft), entity);
        return draft;
    }

    private EvaluatorSpec toVersion(EvaluatorVersionEntity entity) {
        EvaluatorSpec spec = EvaluatorSpec.fromPayload(
                StorageModels.parsePayload(entity.getPayload()));
        verifyIndexed(toVersionEntity(spec), entity);
        return spec;
    }

    private EvaluatorEntity toEntity(Evaluator evaluator) {
        EvaluatorEntity entity = new EvaluatorEntity();
        entity.setIdKey(IdentityDigest.of(evaluator.id()));
        entity.setId(evaluator.id());
        entity.setSource(evaluator.source().wireValue());
        entity.setEnabled(evaluator.enabled() ? 1 : 0);
        entity.setCreatedAt(evaluator.createdAt().toLocalDateTime());
        entity.setUpdatedAt(evaluator.updatedAt().toLocalDateTime());
        entity.setUserTeamKey(IdentityDigest.of(evaluator.userTeamId()));
        entity.setUserTeamId(evaluator.userTeamId());
        entity.setUserId(evaluator.userId());
        entity.setUserName(evaluator.userName());
        entity.setPayload(CanonicalJson.serialize(evaluator.toPayload()));
        return entity;
    }

    private EvaluatorDraftEntity toDraftEntity(EvaluatorDraft draft) {
        EvaluatorDraftEntity entity = new EvaluatorDraftEntity();
        entity.setIdKey(IdentityDigest.of(draft.id()));
        entity.setId(draft.id());
        entity.setEvaluatorKey(IdentityDigest.of(draft.evaluatorId()));
        entity.setEvaluatorId(draft.evaluatorId());
        entity.setCreatedAt(draft.createdAt().toLocalDateTime());
        entity.setUpdatedAt(draft.updatedAt().toLocalDateTime());
        entity.setUserTeamKey(IdentityDigest.of(draft.userTeamId()));
        entity.setUserTeamId(draft.userTeamId());
        entity.setUserId(draft.userId());
        entity.setUserName(draft.userName());
        entity.setPayload(CanonicalJson.serialize(draft.toPayload()));
        return entity;
    }

    private EvaluatorVersionEntity toVersionEntity(EvaluatorSpec spec) {
        EvaluatorVersionEntity entity = new EvaluatorVersionEntity();
        entity.setEvaluatorKey(IdentityDigest.of(spec.id()));
        entity.setVersion(EvaluatorVersioning.parseVersion(spec.version()));
        entity.setEvaluatorId(spec.id());
        entity.setContentSha256(spec.contentSha256());
        entity.setUserTeamKey(IdentityDigest.of(spec.userTeamId()));
        entity.setUserTeamId(spec.userTeamId());
        entity.setUserId(spec.userId());
        entity.setUserName(spec.userName());
        entity.setPayload(CanonicalJson.serialize(spec.toPayload()));
        return entity;
    }

    private static void verifyIndexed(EvaluatorEntity rebuilt, EvaluatorEntity stored) {
        if (!Arrays.equals(rebuilt.getIdKey(), stored.getIdKey())
                || !equalsNullable(rebuilt.getId(), stored.getId())
                || !equalsNullable(rebuilt.getSource(), stored.getSource())
                || !equalsNullable(rebuilt.getEnabled(), stored.getEnabled())
                || !equalsNullable(rebuilt.getCreatedAt(), stored.getCreatedAt())
                || !equalsNullable(rebuilt.getUpdatedAt(), stored.getUpdatedAt())
                || !Arrays.equals(rebuilt.getUserTeamKey(), stored.getUserTeamKey())
                || !equalsNullable(rebuilt.getUserTeamId(), stored.getUserTeamId())
                || !equalsNullable(rebuilt.getUserId(), stored.getUserId())
                || !equalsNullable(rebuilt.getUserName(), stored.getUserName())) {
            throw new IllegalArgumentException("stored indexed columns do not match payload");
        }
    }

    private static void verifyIndexed(EvaluatorDraftEntity rebuilt, EvaluatorDraftEntity stored) {
        if (!Arrays.equals(rebuilt.getIdKey(), stored.getIdKey())
                || !equalsNullable(rebuilt.getId(), stored.getId())
                || !Arrays.equals(rebuilt.getEvaluatorKey(), stored.getEvaluatorKey())
                || !equalsNullable(rebuilt.getEvaluatorId(), stored.getEvaluatorId())
                || !equalsNullable(rebuilt.getCreatedAt(), stored.getCreatedAt())
                || !equalsNullable(rebuilt.getUpdatedAt(), stored.getUpdatedAt())
                || !Arrays.equals(rebuilt.getUserTeamKey(), stored.getUserTeamKey())
                || !equalsNullable(rebuilt.getUserTeamId(), stored.getUserTeamId())
                || !equalsNullable(rebuilt.getUserId(), stored.getUserId())
                || !equalsNullable(rebuilt.getUserName(), stored.getUserName())) {
            throw new IllegalArgumentException("stored indexed columns do not match payload");
        }
    }

    private static void verifyIndexed(EvaluatorVersionEntity rebuilt,
            EvaluatorVersionEntity stored) {
        if (!Arrays.equals(rebuilt.getEvaluatorKey(), stored.getEvaluatorKey())
                || !equalsNullable(rebuilt.getVersion(), stored.getVersion())
                || !equalsNullable(rebuilt.getEvaluatorId(), stored.getEvaluatorId())
                || !equalsNullable(rebuilt.getContentSha256(), stored.getContentSha256())
                || !Arrays.equals(rebuilt.getUserTeamKey(), stored.getUserTeamKey())
                || !equalsNullable(rebuilt.getUserTeamId(), stored.getUserTeamId())
                || !equalsNullable(rebuilt.getUserId(), stored.getUserId())
                || !equalsNullable(rebuilt.getUserName(), stored.getUserName())) {
            throw new IllegalArgumentException("stored indexed columns do not match payload");
        }
    }
}
