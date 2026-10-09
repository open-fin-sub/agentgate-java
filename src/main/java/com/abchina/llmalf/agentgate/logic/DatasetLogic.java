package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.dao.DatasetDAO;
import com.abchina.llmalf.agentgate.dao.DatasetVersionDAO;
import com.abchina.llmalf.agentgate.dao.entity.DatasetEntity;
import com.abchina.llmalf.agentgate.dao.entity.DatasetVersionEntity;
import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.IdentityDigest;
import com.abchina.llmalf.agentgate.domain.model.dataset.Dataset;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersionStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 数据集域存储编排.
 *
 * <p>对齐 Python storage/mysql.py 的 Dataset/DatasetVersion 系列:
 * 乐观并发(stale/不可变检查)、payload 一致性守护、digest 冲突检测
 * 与内存排序;消息文本逐字一致。</p>
 */
@Component
public class DatasetLogic {

    private final DatasetDAO datasetDAO;
    private final DatasetVersionDAO datasetVersionDAO;

    public DatasetLogic(DatasetDAO datasetDAO, DatasetVersionDAO datasetVersionDAO) {
        this.datasetDAO = datasetDAO;
        this.datasetVersionDAO = datasetVersionDAO;
    }

    /**
     * 保存数据集目录(存在时 created_at 不可变且不得回退 updated_at).
     *
     * @param dataset 数据集
     */
    @Transactional
    public void saveDataset(Dataset dataset) {
        try {
            DatasetEntity storedEntity = datasetDAO.selectByIdForUpdate(
                    IdentityDigest.of(dataset.id()));
            Dataset stored = storedEntity == null ? null : toDataset(storedEntity);
            if (stored != null) {
                if (!dataset.createdAt().equals(stored.createdAt())) {
                    throw new IllegalArgumentException("Dataset created_at is immutable");
                }
                if (dataset.updatedAt().isBefore(stored.updatedAt())) {
                    throw new IllegalArgumentException("cannot save a stale Dataset");
                }
                datasetDAO.updateById(toEntity(dataset));
            } else {
                datasetDAO.insert(toEntity(dataset));
            }
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 保存数据集与其首个版本(同一事务).
     *
     * @param dataset 数据集
     * @param version 版本
     */
    @Transactional
    public void saveDatasetWithVersion(Dataset dataset, DatasetVersion version) {
        if (!version.datasetId().equals(dataset.id())) {
            throw new IllegalArgumentException("DatasetVersion must belong to Dataset");
        }
        try {
            datasetDAO.insert(toEntity(dataset));
            datasetVersionDAO.insert(toVersionEntity(version));
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 查询数据集.
     *
     * @param datasetId 数据集 id
     * @param userTeamId 团队 id
     * @return 数据集(可空)
     */
    @Transactional
    public Dataset getDataset(String datasetId, String userTeamId) {
        DatasetEntity entity = datasetDAO.selectByKey(IdentityDigest.of(datasetId));
        if (entity == null) {
            return null;
        }
        if (!entity.getId().equals(datasetId)) {
            throw new IllegalArgumentException("database identity digest collision");
        }
        if (!Arrays.equals(entity.getUserTeamKey(), IdentityDigest.of(userTeamId))) {
            return null;
        }
        return toDataset(entity);
    }

    /**
     * 列出团队数据集(updated_at 降序,id 升序).
     *
     * @param includeArchived 是否包含归档
     * @param userTeamId 团队 id
     * @return 数据集列表
     */
    @Transactional
    public List<Dataset> listDatasets(boolean includeArchived, String userTeamId) {
        List<Dataset> items = new ArrayList<>();
        for (DatasetEntity entity : datasetDAO.selectByTeamKey(IdentityDigest.of(userTeamId))) {
            if (!entity.getUserTeamId().equals(userTeamId)) {
                throw new IllegalArgumentException("database identity digest collision");
            }
            items.add(toDataset(entity));
        }
        List<Dataset> visible = new ArrayList<>();
        for (Dataset item : items) {
            if (includeArchived || !item.archived()) {
                visible.add(item);
            }
        }
        return StorageOrdering.ordered(visible, Dataset::updatedAt, Dataset::id);
    }

    /**
     * 保存数据集版本(发布态不可变,草稿态可更新且有 stale 检查).
     *
     * @param version 版本
     */
    @Transactional
    public void saveDatasetVersion(DatasetVersion version) {
        try {
            DatasetEntity parent = datasetDAO.selectByIdForUpdate(
                    IdentityDigest.of(version.datasetId()));
            if (parent == null) {
                throw new IllegalArgumentException("unknown Dataset");
            }
            DatasetVersionEntity storedEntity = datasetVersionDAO.selectByIdForUpdate(
                    IdentityDigest.of(version.id()));
            DatasetVersion stored = storedEntity == null ? null : toVersion(storedEntity);
            if (stored != null) {
                if (stored.status() == DatasetVersionStatus.PUBLISHED) {
                    if (!StorageModels.samePayload(stored.toPayload(), version.toPayload())) {
                        throw new IllegalArgumentException("published DatasetVersion is immutable");
                    }
                    return;
                }
                if (version.status() != DatasetVersionStatus.DRAFT) {
                    throw new IllegalArgumentException(
                            "draft DatasetVersion cannot be published through save");
                }
                if (!version.datasetId().equals(stored.datasetId())
                        || !version.createdAt().equals(stored.createdAt())) {
                    throw new IllegalArgumentException(
                            "DatasetVersion identity and created_at are immutable");
                }
                if (version.updatedAt().isBefore(stored.updatedAt())) {
                    throw new IllegalArgumentException("cannot save a stale DatasetVersion draft");
                }
                datasetVersionDAO.updateById(toVersionEntity(version));
            } else {
                datasetVersionDAO.insert(toVersionEntity(version));
            }
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 列出数据集版本(草稿优先,同组版本号降序).
     *
     * @param datasetId 数据集 id
     * @param includeDraft 是否包含草稿
     * @param userTeamId 团队 id
     * @return 版本列表
     */
    @Transactional
    public List<DatasetVersion> listDatasetVersions(String datasetId, boolean includeDraft,
            String userTeamId) {
        List<DatasetVersion> items = new ArrayList<>();
        for (DatasetVersionEntity entity : datasetVersionDAO.selectByDatasetKeyTeamKey(
                IdentityDigest.of(datasetId), IdentityDigest.of(userTeamId))) {
            if (!entity.getDatasetId().equals(datasetId)
                    || !entity.getUserTeamId().equals(userTeamId)) {
                throw new IllegalArgumentException("database identity digest collision");
            }
            items.add(toVersion(entity));
        }
        List<DatasetVersion> visible = new ArrayList<>();
        for (DatasetVersion item : items) {
            if (includeDraft || item.status() == DatasetVersionStatus.PUBLISHED) {
                visible.add(item);
            }
        }
        visible.sort(Comparator
                .comparing((DatasetVersion v) -> v.status() != DatasetVersionStatus.DRAFT)
                .thenComparing(v -> -(v.version() == null ? 0 : v.version())));
        return visible;
    }

    /**
     * 查询草稿.
     *
     * @param datasetId 数据集 id
     * @param userTeamId 团队 id
     * @return 草稿(可空)
     */
    @Transactional
    public DatasetVersion getDatasetDraft(String datasetId, String userTeamId) {
        return findOne(listDatasetVersions(datasetId, true, userTeamId),
                item -> item.status() == DatasetVersionStatus.DRAFT);
    }

    /**
     * 查询指定发布版本.
     *
     * @param datasetId 数据集 id
     * @param version 版本号
     * @param userTeamId 团队 id
     * @return 版本(可空)
     */
    @Transactional
    public DatasetVersion getPublishedDatasetVersion(String datasetId, int version,
            String userTeamId) {
        return findOne(listDatasetVersions(datasetId, false, userTeamId),
                item -> item.version() != null && item.version() == version);
    }

    /**
     * 查询最新发布版本.
     *
     * @param datasetId 数据集 id
     * @param userTeamId 团队 id
     * @return 版本(可空)
     */
    @Transactional
    public DatasetVersion getLatestPublishedDatasetVersion(String datasetId, String userTeamId) {
        List<DatasetVersion> items = listDatasetVersions(datasetId, false, userTeamId);
        return items.isEmpty() ? null : items.get(0);
    }

    /**
     * 删除草稿(带期望草稿 id 防误删).
     *
     * @param datasetId 数据集 id
     * @param expectedDraftId 期望草稿 id
     * @param userTeamId 团队 id
     */
    @Transactional
    public void deleteDatasetDraft(String datasetId, String expectedDraftId, String userTeamId) {
        datasetDAO.selectByIdForUpdate(IdentityDigest.of(datasetId));
        DatasetVersion draft = lockDraft(expectedDraftId, datasetId, userTeamId);
        if (draft == null) {
            throw new IllegalArgumentException("expected Dataset draft does not exist");
        }
        datasetVersionDAO.deleteByKey(IdentityDigest.of(draft.id()));
    }

    /**
     * 以发布版本替换草稿(同一事务:插入发布版本 + 删除草稿).
     *
     * @param expectedDraftId 期望草稿 id
     * @param published 发布版本
     */
    @Transactional
    public void replaceDatasetDraft(String expectedDraftId, DatasetVersion published) {
        try {
            datasetDAO.selectByIdForUpdate(IdentityDigest.of(published.datasetId()));
            DatasetVersion draft = lockDraft(expectedDraftId, published.datasetId(),
                    published.userTeamId());
            if (draft == null) {
                throw new IllegalArgumentException("expected Dataset draft does not exist");
            }
            if (published.status() != DatasetVersionStatus.PUBLISHED
                    || published.id().equals(draft.id())) {
                throw new IllegalArgumentException(
                        "replacement must be a publication with a new identity");
            }
            String changedField = firstChangedDraftField(draft, published);
            if (changedField != null) {
                throw new IllegalArgumentException(
                        "published DatasetVersion must preserve draft " + changedField);
            }
            if (published.updatedAt().isBefore(draft.updatedAt())) {
                throw new IllegalArgumentException("cannot replace a newer DatasetVersion draft");
            }
            datasetVersionDAO.insert(toVersionEntity(published));
            datasetVersionDAO.deleteByKey(IdentityDigest.of(draft.id()));
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    private DatasetVersion lockDraft(String draftId, String datasetId, String userTeamId) {
        DatasetVersionEntity entity = datasetVersionDAO.selectByIdForUpdate(
                IdentityDigest.of(draftId));
        if (entity == null) {
            return null;
        }
        DatasetVersion version = toVersion(entity);
        if (version.status() != DatasetVersionStatus.DRAFT
                || !version.datasetId().equals(datasetId)
                || !version.userTeamId().equals(userTeamId)) {
            return null;
        }
        return version;
    }

    private static DatasetVersion findOne(List<DatasetVersion> items,
            Predicate<DatasetVersion> filter) {
        List<DatasetVersion> matched = new ArrayList<>();
        for (DatasetVersion item : items) {
            if (filter.test(item)) {
                matched.add(item);
            }
        }
        if (matched.size() > 1) {
            throw new IllegalArgumentException("expected a unique stored identity");
        }
        return matched.isEmpty() ? null : matched.get(0);
    }

    private static boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String firstChangedDraftField(DatasetVersion draft, DatasetVersion published) {
        if (!draft.datasetId().equals(published.datasetId())) {
            return "dataset_id";
        }
        if (!draft.contentSha256().equals(published.contentSha256())) {
            return "content_sha256";
        }
        if (!draft.createdAt().equals(published.createdAt())) {
            return "created_at";
        }
        if (!equalsNullable(draft.basedOnVersion(), published.basedOnVersion())) {
            return "based_on_version";
        }
        return null;
    }

    private Dataset toDataset(DatasetEntity entity) {
        Dataset dataset = Dataset.fromPayload(StorageModels.parsePayload(entity.getPayload()));
        verifyIndexed(toEntity(dataset), entity);
        return dataset;
    }

    private DatasetVersion toVersion(DatasetVersionEntity entity) {
        DatasetVersion version = DatasetVersion.fromPayload(
                StorageModels.parsePayload(entity.getPayload()));
        verifyIndexed(toVersionEntity(version), entity);
        return version;
    }

    private DatasetEntity toEntity(Dataset dataset) {
        DatasetEntity entity = new DatasetEntity();
        entity.setIdKey(IdentityDigest.of(dataset.id()));
        entity.setId(dataset.id());
        entity.setName(dataset.name());
        entity.setArchived(dataset.archived() ? 1 : 0);
        entity.setUpdatedAt(dataset.updatedAt().toLocalDateTime());
        entity.setUserTeamKey(IdentityDigest.of(dataset.userTeamId()));
        entity.setUserTeamId(dataset.userTeamId());
        entity.setUserId(dataset.userId());
        entity.setUserName(dataset.userName());
        entity.setPayload(CanonicalJson.serialize(dataset.toPayload()));
        return entity;
    }

    private DatasetVersionEntity toVersionEntity(DatasetVersion version) {
        DatasetVersionEntity entity = new DatasetVersionEntity();
        entity.setIdKey(IdentityDigest.of(version.id()));
        entity.setId(version.id());
        entity.setDatasetKey(IdentityDigest.of(version.datasetId()));
        entity.setDatasetId(version.datasetId());
        entity.setVersion(version.version() == null ? null : version.version().longValue());
        entity.setStatus(version.status().wireValue());
        entity.setDraftSlot(version.status() == DatasetVersionStatus.DRAFT ? 1 : null);
        entity.setCreatedAt(version.createdAt().toLocalDateTime());
        entity.setContentSha256(version.contentSha256());
        entity.setUserTeamKey(IdentityDigest.of(version.userTeamId()));
        entity.setUserTeamId(version.userTeamId());
        entity.setUserId(version.userId());
        entity.setUserName(version.userName());
        entity.setPayload(CanonicalJson.serialize(version.toPayload()));
        return entity;
    }

    private static void verifyIndexed(DatasetEntity rebuilt, DatasetEntity stored) {
        if (!Arrays.equals(rebuilt.getIdKey(), stored.getIdKey())
                || !equalsNullable(rebuilt.getId(), stored.getId())
                || !equalsNullable(rebuilt.getName(), stored.getName())
                || !equalsNullable(rebuilt.getArchived(), stored.getArchived())
                || !equalsNullable(rebuilt.getUpdatedAt(), stored.getUpdatedAt())
                || !Arrays.equals(rebuilt.getUserTeamKey(), stored.getUserTeamKey())
                || !equalsNullable(rebuilt.getUserTeamId(), stored.getUserTeamId())
                || !equalsNullable(rebuilt.getUserId(), stored.getUserId())
                || !equalsNullable(rebuilt.getUserName(), stored.getUserName())) {
            throw new IllegalArgumentException("stored indexed columns do not match payload");
        }
    }

    private static void verifyIndexed(DatasetVersionEntity rebuilt, DatasetVersionEntity stored) {
        if (!Arrays.equals(rebuilt.getIdKey(), stored.getIdKey())
                || !equalsNullable(rebuilt.getId(), stored.getId())
                || !Arrays.equals(rebuilt.getDatasetKey(), stored.getDatasetKey())
                || !equalsNullable(rebuilt.getDatasetId(), stored.getDatasetId())
                || !equalsNullable(rebuilt.getVersion(), stored.getVersion())
                || !equalsNullable(rebuilt.getStatus(), stored.getStatus())
                || !equalsNullable(rebuilt.getDraftSlot(), stored.getDraftSlot())
                || !equalsNullable(rebuilt.getCreatedAt(), stored.getCreatedAt())
                || !equalsNullable(rebuilt.getContentSha256(), stored.getContentSha256())
                || !Arrays.equals(rebuilt.getUserTeamKey(), stored.getUserTeamKey())
                || !equalsNullable(rebuilt.getUserTeamId(), stored.getUserTeamId())
                || !equalsNullable(rebuilt.getUserId(), stored.getUserId())
                || !equalsNullable(rebuilt.getUserName(), stored.getUserName())) {
            throw new IllegalArgumentException("stored indexed columns do not match payload");
        }
    }
}
