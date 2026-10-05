package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.UserContext;
import com.abchina.llmalf.agentgate.common.UserContextHolder;
import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.dataset.Dataset;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersionStatus;
import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.logic.DatasetLogic;
import com.abchina.llmalf.agentgate.service.format.DatasetJsonFormat;
import com.abchina.llmalf.agentgate.service.format.DatasetXlsxFormat;
import com.abchina.llmalf.agentgate.logic.DatasetVersioning;
import com.abchina.llmalf.agentgate.service.IDatasetService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 数据集管理服务实现.
 *
 * <p>对齐 Python DatasetManagement:团队上下文经 UserContextHolder 注入
 * (对应 get_user_info),域校验消息逐字一致。</p>
 */
@Service
public class DatasetServiceImpl implements IDatasetService {

    private final DatasetLogic datasetLogic;

    public DatasetServiceImpl(DatasetLogic datasetLogic) {
        this.datasetLogic = datasetLogic;
    }

    @Override
    public List<Map<String, Object>> listDatasetSummaries(boolean includeArchived) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Dataset dataset : datasetLogic.listDatasets(includeArchived, teamId())) {
            List<DatasetVersion> versions = datasetLogic.listDatasetVersions(
                    dataset.id(), true, teamId());
            DatasetVersion latest = null;
            for (DatasetVersion item : versions) {
                if (item.status() == DatasetVersionStatus.PUBLISHED) {
                    latest = item;
                    break;
                }
            }
            boolean hasDraft = false;
            for (DatasetVersion item : versions) {
                if (item.status() == DatasetVersionStatus.DRAFT) {
                    hasDraft = true;
                    break;
                }
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> summary =
                    new LinkedHashMap<>((Map<String, Object>) dataset.toPayload());
            summary.put("version", latest == null ? null : latest.version());
            summary.put("case_count", latest == null ? 0 : latest.cases().size());
            summary.put("has_draft", hasDraft);
            summaries.add(summary);
        }
        return summaries;
    }

    @Override
    public Dataset getDataset(String datasetId) {
        Dataset dataset = datasetLogic.getDataset(datasetId, teamId());
        if (dataset == null) {
            throw new IllegalArgumentException("unknown Dataset: " + datasetId);
        }
        return dataset;
    }

    @Override
    public Dataset createDataset(String name, String description) {
        UserContext context = UserContextHolder.current();
        Dataset dataset = Dataset.of(UUID.randomUUID().toString(), name.trim(),
                description == null ? "" : description.trim(), false, null, null,
                context.userTeamId(), context.userId(), context.userName());
        datasetLogic.saveDataset(dataset);
        return dataset;
    }

    @Override
    public Dataset updateDataset(String datasetId, String name, String description,
            Boolean archived) {
        Dataset dataset = getDataset(datasetId);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                new LinkedHashMap<>((Map<String, Object>) dataset.toPayload());
        if (name != null) {
            payload.put("name", name.trim());
        }
        if (description != null) {
            payload.put("description", description.trim());
        }
        if (archived != null) {
            payload.put("archived", archived);
        }
        payload.put("updated_at", DomainValidations.isoFormat(DomainValidations.utcNow()));
        Dataset updated = Dataset.fromPayload(payload);
        datasetLogic.saveDataset(updated);
        return updated;
    }

    @Override
    public Dataset archiveDataset(String datasetId) {
        return updateDataset(datasetId, null, null, Boolean.TRUE);
    }

    @Override
    public List<DatasetVersion> listVersions(String datasetId) {
        getDataset(datasetId);
        return datasetLogic.listDatasetVersions(datasetId, true, teamId());
    }

    @Override
    public DatasetVersion getVersion(String datasetId, int version) {
        getDataset(datasetId);
        DatasetVersion item = datasetLogic.getPublishedDatasetVersion(datasetId, version,
                teamId());
        if (item == null) {
            throw new IllegalArgumentException(
                    "unknown Dataset version: " + datasetId + " v" + version);
        }
        return item;
    }

    @Override
    public DatasetVersion getDraft(String datasetId) {
        getDataset(datasetId);
        return datasetLogic.getDatasetDraft(datasetId, teamId());
    }

    @Override
    public DatasetVersion createDraft(String datasetId, Integer basedOnVersion) {
        Dataset dataset = getDataset(datasetId);
        if (dataset.archived()) {
            throw new IllegalArgumentException("archived Dataset cannot be edited");
        }
        if (datasetLogic.getDatasetDraft(datasetId, teamId()) != null) {
            throw new IllegalArgumentException("Dataset already has an active draft");
        }
        DatasetVersion base = basedOnVersion != null
                ? getVersion(datasetId, basedOnVersion)
                : datasetLogic.getLatestPublishedDatasetVersion(datasetId, teamId());
        DatasetVersion draft = DatasetVersioning.createDraft(dataset, base,
                UUID.randomUUID().toString(), DomainValidations.utcNow());
        datasetLogic.saveDatasetVersion(draft);
        return draft;
    }

    @Override
    public void discardDraft(String datasetId) {
        DatasetVersion draft = requireDraft(datasetId);
        datasetLogic.deleteDatasetDraft(datasetId, draft.id(), teamId());
    }

    @Override
    public DatasetVersion saveCase(String datasetId, Case caseItem) {
        DatasetVersion updated = DatasetVersioning.withCase(requireDraft(datasetId), caseItem,
                DomainValidations.utcNow());
        datasetLogic.saveDatasetVersion(updated);
        return updated;
    }

    @Override
    public DatasetVersion removeCase(String datasetId, String caseId) {
        DatasetVersion updated = DatasetVersioning.removeCase(requireDraft(datasetId), caseId,
                DomainValidations.utcNow());
        datasetLogic.saveDatasetVersion(updated);
        return updated;
    }

    @Override
    public DatasetVersion copyCase(String datasetId, String caseId) {
        DatasetVersion draft = requireDraft(datasetId);
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
        DatasetVersion updated = DatasetVersioning.copyCase(draft, caseId,
                UUID.randomUUID().toString(), source.name() + "（副本）",
                DomainValidations.utcNow());
        datasetLogic.saveDatasetVersion(updated);
        return updated;
    }

    @Override
    public DatasetVersion reorderCases(String datasetId, List<String> caseIds) {
        DatasetVersion updated = DatasetVersioning.reorderCases(requireDraft(datasetId),
                caseIds, DomainValidations.utcNow());
        datasetLogic.saveDatasetVersion(updated);
        return updated;
    }

    @Override
    public DatasetVersion publishDraft(String datasetId) {
        DatasetVersion draft = requireDraft(datasetId);
        DatasetVersion latest = datasetLogic.getLatestPublishedDatasetVersion(datasetId,
                teamId());
        int nextVersion = (latest == null || latest.version() == null ? 0 : latest.version()) + 1;
        DatasetVersion published = DatasetVersioning.publishDraft(draft,
                UUID.randomUUID().toString(), nextVersion, DomainValidations.utcNow());
        datasetLogic.replaceDatasetDraft(draft.id(), published);
        return published;
    }

    @Override
    public Map<String, Object> copyDataset(String sourceDatasetId, String name,
            Integer sourceVersion) {
        DatasetVersion source = sourceVersion != null
                ? getVersion(sourceDatasetId, sourceVersion)
                : latestPublished(sourceDatasetId);
        UserContext context = UserContextHolder.current();
        Dataset dataset = Dataset.of(UUID.randomUUID().toString(), name.trim(), "", false,
                null, null, context.userTeamId(), context.userId(), context.userName());
        List<Case> cases = new ArrayList<>(source.cases().size());
        for (Case item : source.cases()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload =
                    new LinkedHashMap<>((Map<String, Object>) item.toPayload());
            payload.put("id", UUID.randomUUID().toString());
            cases.add(Case.fromPayload(payload));
        }
        DatasetVersion draft = DatasetVersion.of(UUID.randomUUID().toString(), dataset.id(),
                dataset.name(), dataset.description(), null, DatasetVersionStatus.DRAFT, null,
                cases, "Copied from " + sourceDatasetId + " v" + source.version(),
                null, null, null, "", context.userTeamId(), context.userId(),
                context.userName());
        datasetLogic.saveDatasetWithVersion(dataset, draft);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataset", dataset.toPayload());
        result.put("draft", draft.toPayload());
        return result;
    }


    @Override
    public Map<String, Object> importJson(Map<String, Object> document) {
        DatasetJsonFormat.validateEnvelope(document);
        @SuppressWarnings("unchecked")
        Map<String, Object> datasetPayload = (Map<String, Object>) document.get("dataset");
        @SuppressWarnings("unchecked")
        Map<String, Object> versionPayload = (Map<String, Object>) document.get("version");
        Dataset dataset = Dataset.fromPayload(datasetPayload);
        DatasetVersion version = DatasetVersion.fromPayload(versionPayload);
        if (!dataset.id().equals(version.datasetId())) {
            throw new IllegalArgumentException("Dataset and DatasetVersion identities do not match");
        }
        UserContext context = UserContextHolder.current();
        datasetPayload = withUser(datasetPayload, context);
        versionPayload = withUser(versionPayload, context);
        dataset = Dataset.fromPayload(datasetPayload);
        version = DatasetVersion.fromPayload(versionPayload);
        if (datasetLogic.getDataset(dataset.id(), context.userTeamId()) != null) {
            throw new IllegalArgumentException("Dataset already exists: " + dataset.id());
        }
        datasetLogic.saveDatasetWithVersion(dataset, version);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataset", dataset.toPayload());
        result.put("version", version.toPayload());
        return result;
    }

    @Override
    public Map<String, Object> importXlsx(byte[] content, String name, String description) {
        List<Map<String, Object>> cases = DatasetXlsxFormat.parse(content);
        UserContext context = UserContextHolder.current();
        Dataset dataset = Dataset.of(UUID.randomUUID().toString(), name.trim(),
                description == null ? "" : description.trim(), false, null, null,
                context.userTeamId(), context.userId(), context.userName());
        List<Case> parsedCases = new ArrayList<>(cases.size());
        for (Map<String, Object> casePayload : cases) {
            parsedCases.add(Case.fromPayload(casePayload));
        }
        DatasetVersion draft = DatasetVersion.of(UUID.randomUUID().toString(), dataset.id(),
                dataset.name(), dataset.description(), null, DatasetVersionStatus.DRAFT, null,
                parsedCases, "", null, null, null, "", context.userTeamId(), context.userId(),
                context.userName());
        datasetLogic.saveDatasetWithVersion(dataset, draft);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataset", dataset.toPayload());
        result.put("version", draft.toPayload());
        return result;
    }

    @Override
    public ExportedVersion exportVersion(String datasetId, int version, String formatName) {
        Dataset dataset = getDataset(datasetId);
        DatasetVersion published = getVersion(datasetId, version);
        if (!dataset.id().equals(published.datasetId())) {
            throw new IllegalArgumentException("Dataset and DatasetVersion identities do not match");
        }
        byte[] content;
        String mediaType;
        String extension;
        if ("json".equals(formatName)) {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("format", DatasetJsonFormat.FORMAT_NAME);
            envelope.put("format_version", DatasetJsonFormat.FORMAT_VERSION);
            envelope.put("dataset", dataset.toPayload());
            envelope.put("version", published.toPayload());
            content = CanonicalJson.serialize(envelope).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            mediaType = "application/json";
            extension = "json";
        } else if ("xlsx".equals(formatName)) {
            List<Object> casePayloads = new ArrayList<>(published.cases().size());
            for (Case item : published.cases()) {
                casePayloads.add(item.toPayload());
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> typedCases = (List<Map<String, Object>>) (List<?>) casePayloads;
            content = DatasetXlsxFormat.dump(typedCases);
            mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            extension = "xlsx";
        } else {
            throw new IllegalArgumentException(
                    "unsupported Dataset output format: '" + formatName + "'");
        }
        String versionLabel = published.version() == null ? "draft" : "v" + published.version();
        return new ExportedVersion(content, mediaType,
                safeFilenameStem(dataset.name()) + "-" + versionLabel + "." + extension,
                published.contentSha256());
    }

    private static Map<String, Object> withUser(Map<String, Object> payload, UserContext context) {
        Map<String, Object> updated = new LinkedHashMap<>(payload);
        updated.put("user_team_id", context.userTeamId());
        updated.put("user_id", context.userId());
        updated.put("user_name", context.userName());
        return updated;
    }

    private static String safeFilenameStem(String name) {
        String sanitized = name.replaceAll("[^A-Za-z0-9._-]+", "-");
        sanitized = sanitized.replaceAll("^\\.+", "").replaceAll("\\.+$", "");
        sanitized = sanitized.replaceAll("^-+", "").replaceAll("-+$", "");
        return sanitized.isEmpty() ? "dataset" : sanitized;
    }

    private DatasetVersion latestPublished(String datasetId) {
        getDataset(datasetId);
        DatasetVersion version = datasetLogic.getLatestPublishedDatasetVersion(datasetId,
                teamId());
        if (version == null) {
            throw new IllegalArgumentException(
                    "Dataset has no published version: " + datasetId);
        }
        return version;
    }

    private DatasetVersion requireDraft(String datasetId) {
        DatasetVersion draft = getDraft(datasetId);
        if (draft == null) {
            throw new IllegalArgumentException("Dataset has no active draft");
        }
        return draft;
    }

    private static String teamId() {
        return UserContextHolder.current().userTeamId();
    }
}
