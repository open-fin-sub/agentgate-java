package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.dataset.Dataset;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersionStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据集草稿纯转换.
 *
 * <p>对齐 Python dataset/versioning.py:草稿编辑/复制/重排/发布组装;
 * 更新走 payload 覆写 + content_sha256 置空重算语义。</p>
 */
public final class DatasetVersioning {

    private DatasetVersioning() {
    }

    /**
     * 由目录元数据与可选基线发布版创建草稿.
     *
     * @param dataset 数据集目录
     * @param base 基线发布版(可空)
     * @param draftId 草稿 id
     * @param createdAt 创建时间
     * @return 草稿
     */
    public static DatasetVersion createDraft(Dataset dataset, DatasetVersion base,
            String draftId, OffsetDateTime createdAt) {
        if (base != null) {
            if (base.status() != DatasetVersionStatus.PUBLISHED) {
                throw new IllegalArgumentException("Dataset draft base must be published");
            }
            if (!base.datasetId().equals(dataset.id())) {
                throw new IllegalArgumentException(
                        "Dataset draft base belongs to another Dataset");
            }
        }
        return DatasetVersion.of(draftId, dataset.id(), dataset.name(), dataset.description(),
                base == null ? null : base.basedOnVersion(), DatasetVersionStatus.DRAFT, null,
                base == null ? new ArrayList<Case>() : new ArrayList<>(base.cases()),
                base == null ? "" : base.notes(), createdAt, createdAt, null, "",
                dataset.userTeamId(), dataset.userId(), dataset.userName());
    }

    /**
     * 追加或按稳定身份替换一个用例.
     *
     * @param draft 草稿
     * @param caseItem 用例
     * @param updatedAt 更新时间
     * @return 新草稿
     */
    public static DatasetVersion withCase(DatasetVersion draft, Case caseItem,
            OffsetDateTime updatedAt) {
        List<Case> cases = new ArrayList<>(draft.cases());
        int found = -1;
        for (int i = 0; i < cases.size(); i++) {
            if (cases.get(i).id().equals(caseItem.id())) {
                found = i;
                break;
            }
        }
        if (found < 0) {
            cases.add(caseItem);
        } else {
            cases.set(found, caseItem);
        }
        return updateDraft(draft, updatedAt, cases);
    }

    /**
     * 移除指定用例.
     *
     * @param draft 草稿
     * @param caseId 用例 id
     * @param updatedAt 更新时间
     * @return 新草稿
     */
    public static DatasetVersion removeCase(DatasetVersion draft, String caseId,
            OffsetDateTime updatedAt) {
        List<Case> cases = new ArrayList<>();
        for (Case item : draft.cases()) {
            if (!item.id().equals(caseId)) {
                cases.add(item);
            }
        }
        if (cases.size() == draft.cases().size()) {
            throw new IllegalArgumentException("unknown Case: " + caseId);
        }
        return updateDraft(draft, updatedAt, cases);
    }

    /**
     * 以显式新身份复制一个用例.
     *
     * @param draft 草稿
     * @param caseId 源用例 id
     * @param newCaseId 新用例 id
     * @param newName 新名称
     * @param updatedAt 更新时间
     * @return 新草稿
     */
    public static DatasetVersion copyCase(DatasetVersion draft, String caseId, String newCaseId,
            String newName, OffsetDateTime updatedAt) {
        Case source = null;
        for (Case item : draft.cases()) {
            if (item.id().equals(caseId)) {
                source = item;
                break;
            }
        }
        if (source == null) {
            throw new IllegalArgumentException("unknown Case: " + caseId);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                new LinkedHashMap<>((Map<String, Object>) source.toPayload());
        payload.put("id", newCaseId);
        payload.put("name", newName);
        Case copied = Case.fromPayload(payload);
        return withCase(draft, copied, updatedAt);
    }

    /**
     * 按请求的完整顺序重排用例.
     *
     * @param draft 草稿
     * @param caseIds 用例 id 全集序列
     * @param updatedAt 更新时间
     * @return 新草稿
     */
    public static DatasetVersion reorderCases(DatasetVersion draft, List<String> caseIds,
            OffsetDateTime updatedAt) {
        Map<String, Case> byId = new LinkedHashMap<>();
        for (Case item : draft.cases()) {
            byId.put(item.id(), item);
        }
        Set<String> unique = new HashSet<>(caseIds);
        if (unique.size() != caseIds.size() || !unique.equals(byId.keySet())) {
            throw new IllegalArgumentException(
                    "Case order must contain every draft Case exactly once");
        }
        List<Case> ordered = new ArrayList<>(caseIds.size());
        for (String caseId : caseIds) {
            ordered.add(byId.get(caseId));
        }
        return updateDraft(draft, updatedAt, ordered);
    }

    /**
     * 由草稿内容组装不可变发布版本.
     *
     * @param draft 草稿
     * @param publicationId 发布版 id
     * @param version 版本号
     * @param publishedAt 发布时间
     * @return 发布版
     */
    public static DatasetVersion publishDraft(DatasetVersion draft, String publicationId,
            int version, OffsetDateTime publishedAt) {
        requireDraft(draft);
        if (draft.cases().isEmpty()) {
            throw new IllegalArgumentException(
                    "published DatasetVersion requires at least one Case");
        }
        if (publishedAt.isBefore(draft.updatedAt())) {
            throw new IllegalArgumentException(
                    "published_at must not precede the current draft update");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                new LinkedHashMap<>((Map<String, Object>) draft.toPayload());
        payload.put("id", publicationId);
        payload.put("version", version);
        payload.put("status", DatasetVersionStatus.PUBLISHED.wireValue());
        payload.put("updated_at", DomainValidations.isoFormat(publishedAt));
        payload.put("published_at", DomainValidations.isoFormat(publishedAt));
        payload.put("content_sha256", "");
        return DatasetVersion.fromPayload(payload);
    }

    private static void requireDraft(DatasetVersion version) {
        if (version.status() != DatasetVersionStatus.DRAFT) {
            throw new IllegalArgumentException("DatasetVersion operation requires a draft");
        }
    }

    @SuppressWarnings("unchecked")
    private static DatasetVersion updateDraft(DatasetVersion draft, OffsetDateTime updatedAt,
            List<Case> cases) {
        requireDraft(draft);
        if (updatedAt.isBefore(draft.updatedAt())) {
            throw new IllegalArgumentException(
                    "updated_at must not precede the current draft update");
        }
        Map<String, Object> payload = new LinkedHashMap<>((Map<String, Object>) draft.toPayload());
        List<Object> casePayloads = new ArrayList<>(cases.size());
        for (Case item : cases) {
            casePayloads.add(item.toPayload());
        }
        payload.put("cases", casePayloads);
        payload.put("updated_at", DomainValidations.isoFormat(updatedAt));
        payload.put("content_sha256", "");
        return DatasetVersion.fromPayload(payload);
    }
}
