package com.abchina.llmalf.agentgate.domain.model.artifact;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.FrozenJson;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 执行产物元数据与存储引用.
 *
 * <p>对齐 Python domain/artifact.py::Artifact:可选文本(trace_id/
 * producer_name)空串报错、不归一(与 result 域的归一惯例相反)。</p>
 */
public final class Artifact {

    private final String id;
    private final String runId;
    private final String caseId;
    private final String traceId;
    private final ArtifactProducer producer;
    private final String producerName;
    private final String artifactType;
    private final String filename;
    private final String mediaType;
    private final String storageUri;
    private final String sha256;
    private final long sizeBytes;
    private final OffsetDateTime createdAt;
    private final Map<String, Object> metadata;

    private Artifact(String id, String runId, String caseId, String traceId,
            ArtifactProducer producer, String producerName, String artifactType, String filename,
            String mediaType, String storageUri, String sha256, long sizeBytes,
            OffsetDateTime createdAt, Map<String, Object> metadata) {
        this.id = id;
        this.runId = runId;
        this.caseId = caseId;
        this.traceId = traceId;
        this.producer = producer;
        this.producerName = producerName;
        this.artifactType = artifactType;
        this.filename = filename;
        this.mediaType = mediaType;
        this.storageUri = storageUri;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
        this.metadata = metadata;
    }

    /**
     * 构造产物.
     *
     * @param id 产物 id
     * @param runId Run id
     * @param caseId 用例 id
     * @param traceId Trace id(可空;提供时非空白,空串报错不归一)
     * @param producer 产出方
     * @param producerName 产出方名(可空;提供时非空白)
     * @param artifactType 产物类型
     * @param filename 文件名
     * @param mediaType MIME 类型
     * @param storageUri 存储地址
     * @param sha256 内容摘要(64 位小写十六进制)
     * @param sizeBytes 字节数(非负)
     * @param createdAt 创建时间(可空,取当前 UTC)
     * @param metadata 元数据(冻结)
     * @return 产物实例
     */
    @SuppressWarnings("unchecked")
    public static Artifact of(String id, String runId, String caseId, String traceId,
            ArtifactProducer producer, String producerName, String artifactType, String filename,
            String mediaType, String storageUri, String sha256, long sizeBytes,
            OffsetDateTime createdAt, Map<String, ?> metadata) {
        String validId = DomainValidations.requireNonBlank(id, "Artifact id");
        String validRunId = DomainValidations.requireNonBlank(runId, "Artifact run_id");
        String validCaseId = DomainValidations.requireNonBlank(caseId, "Artifact case_id");
        if (traceId != null) {
            DomainValidations.requireNonBlank(traceId, "Artifact trace_id");
        }
        if (producer == null) {
            throw new IllegalArgumentException("Field required");
        }
        if (producerName != null) {
            DomainValidations.requireNonBlank(producerName, "Artifact producer_name");
        }
        String validArtifactType = DomainValidations.requireNonBlank(artifactType,
                "Artifact artifact_type");
        String validFilename = DomainValidations.requireNonBlank(filename, "Artifact filename");
        String validMediaType = DomainValidations.requireNonBlank(mediaType, "Artifact media_type");
        String validStorageUri = DomainValidations.requireNonBlank(storageUri,
                "Artifact storage_uri");
        DomainValidations.requireSha256(sha256, "Artifact sha256");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("Input should be greater than or equal to 0");
        }
        OffsetDateTime created = DomainValidations.normalizeUtc(
                createdAt == null ? DomainValidations.utcNow() : createdAt,
                "Artifact created_at");
        Map<String, Object> frozenMetadata = metadata == null
                ? Collections.<String, Object>emptyMap()
                : (Map<String, Object>) FrozenJson.freeze(metadata);
        return new Artifact(validId, validRunId, validCaseId, traceId, producer, producerName,
                validArtifactType, validFilename, validMediaType, validStorageUri, sha256,
                sizeBytes, created, frozenMetadata);
    }

    /**
     * 从 payload 解析产物.
     *
     * @param payload payload 对象
     * @return 产物实例
     */
    public static Artifact fromPayload(Map<String, Object> payload) {
        Object rawMetadata = payload.get("metadata");
        Map<String, ?> metadata;
        if (rawMetadata instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, ?> casted = (Map<String, ?>) rawMetadata;
            metadata = casted;
        } else if (rawMetadata == null) {
            metadata = Collections.emptyMap();
        } else {
            throw new IllegalArgumentException("Input should be a valid dictionary");
        }
        return of(PayloadValues.idOrDefault(payload, "id"),
                PayloadValues.requiredString(payload, "run_id"),
                PayloadValues.requiredString(payload, "case_id"),
                PayloadValues.optionalString(payload, "trace_id"),
                ArtifactProducer.fromWireValue(
                        PayloadValues.requiredString(payload, "producer")),
                PayloadValues.optionalString(payload, "producer_name"),
                PayloadValues.requiredString(payload, "artifact_type"),
                PayloadValues.requiredString(payload, "filename"),
                PayloadValues.requiredString(payload, "media_type"),
                PayloadValues.requiredString(payload, "storage_uri"),
                PayloadValues.requiredString(payload, "sha256"),
                longOrDefault(payload, "size_bytes"),
                PayloadValues.optionalOffsetDateTime(payload, "created_at"),
                metadata);
    }

    private static long longOrDefault(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null) {
            throw new IllegalArgumentException("Field required");
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        throw new IllegalArgumentException("Input should be a valid integer");
    }

    public String id() {
        return id;
    }

    public String runId() {
        return runId;
    }

    public String caseId() {
        return caseId;
    }

    public String traceId() {
        return traceId;
    }

    public ArtifactProducer producer() {
        return producer;
    }

    public String producerName() {
        return producerName;
    }

    public String artifactType() {
        return artifactType;
    }

    public String filename() {
        return filename;
    }

    public String mediaType() {
        return mediaType;
    }

    public String storageUri() {
        return storageUri;
    }

    public String sha256() {
        return sha256;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("run_id", runId);
        payload.put("case_id", caseId);
        payload.put("trace_id", traceId);
        payload.put("producer", producer.wireValue());
        payload.put("producer_name", producerName);
        payload.put("artifact_type", artifactType);
        payload.put("filename", filename);
        payload.put("media_type", mediaType);
        payload.put("storage_uri", storageUri);
        payload.put("sha256", sha256);
        payload.put("size_bytes", sizeBytes);
        payload.put("created_at", DomainValidations.isoFormat(createdAt));
        payload.put("metadata", metadata);
        return payload;
    }
}
