package com.abchina.llmalf.agentgate.domain.model.dataset;

import com.abchina.llmalf.agentgate.domain.ContentSha256;
import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 数据集版本快照.
 *
 * <p>对齐 Python domain/dataset.py::DatasetVersion:内容寻址的用例集合快照,
 * content_sha256 由 {dataset_id, cases, notes} 计算并自动填充/校验。</p>
 */
public final class DatasetVersion {

    private final String id;
    private final String datasetId;
    private final String datasetName;
    private final String datasetDescription;
    private final Integer version;
    private final DatasetVersionStatus status;
    private final Integer basedOnVersion;
    private final List<Case> cases;
    private final String notes;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final OffsetDateTime publishedAt;
    private final String contentSha256;
    private final String userTeamId;
    private final String userId;
    private final String userName;

    private DatasetVersion(String id, String datasetId, String datasetName, String datasetDescription,
            Integer version, DatasetVersionStatus status, Integer basedOnVersion, List<Case> cases,
            String notes, OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime publishedAt,
            String contentSha256, String userTeamId, String userId, String userName) {
        this.id = id;
        this.datasetId = datasetId;
        this.datasetName = datasetName;
        this.datasetDescription = datasetDescription;
        this.version = version;
        this.status = status;
        this.basedOnVersion = basedOnVersion;
        this.cases = cases;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.publishedAt = publishedAt;
        this.contentSha256 = contentSha256;
        this.userTeamId = userTeamId;
        this.userId = userId;
        this.userName = userName;
    }

    /**
     * 构造草稿版本便捷工厂(生成 id 与当前时间).
     *
     * @param datasetId 数据集 id
     * @param cases 用例列表(可空)
     * @return 版本实例
     */
    public static DatasetVersion of(String datasetId, List<Case> cases) {
        return of(UUID.randomUUID().toString(), datasetId, "", "", null,
                DatasetVersionStatus.DRAFT, null, cases, "", null, null, null, "", "", "", "");
    }

    /**
     * 构造数据集版本.
     *
     * @param id 版本 id
     * @param datasetId 数据集 id
     * @param datasetName 数据集名快照
     * @param datasetDescription 数据集描述快照
     * @param version 版本号(草稿为 null,发布必填且 ≥1)
     * @param status 状态(默认 DRAFT)
     * @param basedOnVersion 基线版本(可空,必须小于 version)
     * @param cases 用例列表(发布态至少一个)
     * @param notes 备注
     * @param createdAt 创建时间(可空,取当前 UTC)
     * @param updatedAt 更新时间(可空,取当前 UTC)
     * @param publishedAt 发布时间(发布态必填)
     * @param contentSha256 内容摘要(空则自动计算,提供则校验)
     * @param userTeamId 团队 id
     * @param userId 用户 id
     * @param userName 用户名
     * @return 版本实例
     */
    public static DatasetVersion of(String id, String datasetId, String datasetName,
            String datasetDescription, Integer version, DatasetVersionStatus status,
            Integer basedOnVersion, List<Case> cases, String notes, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, OffsetDateTime publishedAt, String contentSha256,
            String userTeamId, String userId, String userName) {
        String validId = DomainValidations.requireNonBlank(id, "DatasetVersion id");
        String validDatasetId = DomainValidations.requireNonBlank(datasetId, "DatasetVersion dataset_id");
        if (version != null && version < 1) {
            throw new IllegalArgumentException("Input should be greater than or equal to 1");
        }
        if (basedOnVersion != null && basedOnVersion < 1) {
            throw new IllegalArgumentException("Input should be greater than or equal to 1");
        }
        DatasetVersionStatus effectiveStatus = status == null
                ? DatasetVersionStatus.DRAFT : status;
        List<Case> frozenCases = cases == null
                ? Collections.<Case>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(cases));
        String effectiveNotes = notes == null ? "" : notes;
        OffsetDateTime created = DomainValidations.normalizeUtc(
                createdAt == null ? DomainValidations.utcNow() : createdAt, "created_at");
        OffsetDateTime updated = DomainValidations.normalizeUtc(
                updatedAt == null ? DomainValidations.utcNow() : updatedAt, "updated_at");
        OffsetDateTime published = publishedAt == null
                ? null : DomainValidations.normalizeUtc(publishedAt, "published_at");

        if (updated.isBefore(created)) {
            throw new IllegalArgumentException("updated_at must not precede created_at");
        }
        if (effectiveStatus == DatasetVersionStatus.DRAFT) {
            if (version != null || published != null) {
                throw new IllegalArgumentException(
                        "draft DatasetVersion cannot be numbered or published");
            }
        } else {
            if (version == null || published == null) {
                throw new IllegalArgumentException(
                        "published DatasetVersion requires version and published_at");
            }
            if (frozenCases.isEmpty()) {
                throw new IllegalArgumentException(
                        "published DatasetVersion requires at least one Case");
            }
            if (published.isBefore(created)) {
                throw new IllegalArgumentException("published_at must not precede created_at");
            }
            if (published.isAfter(updated)) {
                throw new IllegalArgumentException("published_at must not follow updated_at");
            }
            if (basedOnVersion != null && basedOnVersion >= version) {
                throw new IllegalArgumentException("based_on_version must precede version");
            }
        }

        Set<String> caseIds = new HashSet<>();
        for (Case item : frozenCases) {
            if (!caseIds.add(item.id())) {
                throw new IllegalArgumentException("Case ids must be unique within a DatasetVersion");
            }
        }

        String expectedHash = ContentSha256.of(expectedHashPayload(validDatasetId, frozenCases, effectiveNotes));
        String effectiveHash;
        if (contentSha256 != null && !contentSha256.isEmpty()) {
            if (!contentSha256.equals(expectedHash)) {
                throw new IllegalArgumentException("DatasetVersion content hash mismatch");
            }
            effectiveHash = contentSha256;
        } else {
            effectiveHash = expectedHash;
        }

        return new DatasetVersion(validId, validDatasetId,
                datasetName == null ? "" : datasetName,
                datasetDescription == null ? "" : datasetDescription,
                version, effectiveStatus, basedOnVersion, frozenCases, effectiveNotes,
                created, updated, published, effectiveHash,
                userTeamId == null ? "" : userTeamId,
                userId == null ? "" : userId,
                userName == null ? "" : userName);
    }

    /**
     * 从 payload 解析数据集版本.
     *
     * @param payload payload 对象
     * @return 版本实例
     */
    public static DatasetVersion fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.idOrDefault(payload, "id"),
                PayloadValues.requiredString(payload, "dataset_id"),
                PayloadValues.stringOrDefault(payload, "dataset_name", ""),
                PayloadValues.stringOrDefault(payload, "dataset_description", ""),
                PayloadValues.optionalInteger(payload, "version"),
                DatasetVersionStatus.fromWireValue(PayloadValues.stringOrDefault(payload, "status",
                        DatasetVersionStatus.DRAFT.wireValue())),
                PayloadValues.optionalInteger(payload, "based_on_version"),
                parseCases(payload),
                PayloadValues.stringOrDefault(payload, "notes", ""),
                PayloadValues.optionalOffsetDateTime(payload, "created_at"),
                PayloadValues.optionalOffsetDateTime(payload, "updated_at"),
                PayloadValues.optionalOffsetDateTime(payload, "published_at"),
                PayloadValues.stringOrDefault(payload, "content_sha256", ""),
                PayloadValues.stringOrDefault(payload, "user_team_id", ""),
                PayloadValues.stringOrDefault(payload, "user_id", ""),
                PayloadValues.stringOrDefault(payload, "user_name", ""));
    }

    private static List<Case> parseCases(Map<String, Object> payload) {
        Object rawCases = payload == null ? null : payload.get("cases");
        if (rawCases == null) {
            return Collections.emptyList();
        }
        if (!(rawCases instanceof List)) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        List<Case> parsed = new ArrayList<>(((List<?>) rawCases).size());
        for (Object item : (List<?>) rawCases) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Input should be a valid dictionary");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> casePayload = (Map<String, Object>) item;
            parsed.add(Case.fromPayload(casePayload));
        }
        return parsed;
    }

    private static Map<String, Object> expectedHashPayload(String datasetId, List<Case> cases, String notes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataset_id", datasetId);
        List<Object> casePayloads = new ArrayList<>(cases.size());
        for (Case item : cases) {
            casePayloads.add(item.toPayload());
        }
        payload.put("cases", casePayloads);
        payload.put("notes", notes);
        return payload;
    }

    public String id() {
        return id;
    }

    public String datasetId() {
        return datasetId;
    }

    public String datasetName() {
        return datasetName;
    }

    public String datasetDescription() {
        return datasetDescription;
    }

    public Integer version() {
        return version;
    }

    public DatasetVersionStatus status() {
        return status;
    }

    public Integer basedOnVersion() {
        return basedOnVersion;
    }

    public List<Case> cases() {
        return cases;
    }

    public String notes() {
        return notes;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    public OffsetDateTime publishedAt() {
        return publishedAt;
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
        payload.put("dataset_id", datasetId);
        payload.put("dataset_name", datasetName);
        payload.put("dataset_description", datasetDescription);
        payload.put("version", version);
        payload.put("status", status.wireValue());
        payload.put("based_on_version", basedOnVersion);
        List<Object> casePayloads = new ArrayList<>(cases.size());
        for (Case item : cases) {
            casePayloads.add(item.toPayload());
        }
        payload.put("cases", casePayloads);
        payload.put("notes", notes);
        payload.put("created_at", DomainValidations.isoFormat(createdAt));
        payload.put("updated_at", DomainValidations.isoFormat(updatedAt));
        payload.put("published_at", publishedAt == null ? null : DomainValidations.isoFormat(publishedAt));
        payload.put("content_sha256", contentSha256);
        payload.put("user_team_id", userTeamId);
        payload.put("user_id", userId);
        payload.put("user_name", userName);
        return payload;
    }
}
