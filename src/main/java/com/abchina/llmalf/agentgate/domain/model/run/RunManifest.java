package com.abchina.llmalf.agentgate.domain.model.run;

import com.abchina.llmalf.agentgate.domain.ContentSha256;
import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersionStatus;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.domain.model.gate.ReleaseGateSpec;
import com.abchina.llmalf.agentgate.domain.model.metric.MetricPlan;
import com.abchina.llmalf.agentgate.domain.model.target.TargetSnapshot;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评测 Run 不可变执行清单.
 *
 * <p>对齐 Python domain/run.py::RunManifest:精确输入快照与生效配置;
 * manifest_sha256 由 dataset/target/evaluators 等投影结构计算并自动填充/校验。</p>
 */
public final class RunManifest {

    private final DatasetVersion dataset;
    private final List<String> selectedCaseIds;
    private final TargetSnapshot target;
    private final List<EvaluatorSpec> evaluatorSpecs;
    private final List<String> primaryEvaluatorIds;
    private final MetricPlan metricPlan;
    private final ReleaseGateSpec gateSpec;
    private final double timeoutSeconds;
    private final int maxRetries;
    private final int maxParallelCases;
    private final OffsetDateTime createdAt;
    private final String manifestSha256;

    private RunManifest(DatasetVersion dataset, List<String> selectedCaseIds, TargetSnapshot target,
            List<EvaluatorSpec> evaluatorSpecs, List<String> primaryEvaluatorIds,
            MetricPlan metricPlan, ReleaseGateSpec gateSpec, double timeoutSeconds,
            int maxRetries, int maxParallelCases, OffsetDateTime createdAt,
            String manifestSha256) {
        this.dataset = dataset;
        this.selectedCaseIds = selectedCaseIds;
        this.target = target;
        this.evaluatorSpecs = evaluatorSpecs;
        this.primaryEvaluatorIds = primaryEvaluatorIds;
        this.metricPlan = metricPlan;
        this.gateSpec = gateSpec;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
        this.maxParallelCases = maxParallelCases;
        this.createdAt = createdAt;
        this.manifestSha256 = manifestSha256;
    }

    /**
     * 构造执行清单便捷工厂(默认时间/摘要与默认执行参数).
     *
     * @param dataset 已发布数据集版本
     * @param target 目标快照
     * @param evaluatorSpecs 评测器定义列表(至少一个)
     * @param primaryEvaluatorIds 主评测器 id 列表(至少一个)
     * @param metricPlan 指标方案
     * @param gateSpec 门禁配置
     * @return 清单实例
     */
    public static RunManifest of(DatasetVersion dataset, TargetSnapshot target,
            List<EvaluatorSpec> evaluatorSpecs, List<String> primaryEvaluatorIds,
            MetricPlan metricPlan, ReleaseGateSpec gateSpec) {
        return of(dataset, null, target, evaluatorSpecs, primaryEvaluatorIds, metricPlan, gateSpec);
    }

    /**
     * 构造执行清单便捷工厂(默认时间/摘要与默认执行参数).
     *
     * @param dataset 已发布数据集版本
     * @param selectedCaseIds 选用用例 id(可空)
     * @param target 目标快照
     * @param evaluatorSpecs 评测器定义列表(至少一个)
     * @param primaryEvaluatorIds 主评测器 id 列表(至少一个)
     * @param metricPlan 指标方案
     * @param gateSpec 门禁配置
     * @return 清单实例
     */
    public static RunManifest of(DatasetVersion dataset, List<String> selectedCaseIds,
            TargetSnapshot target, List<EvaluatorSpec> evaluatorSpecs,
            List<String> primaryEvaluatorIds, MetricPlan metricPlan, ReleaseGateSpec gateSpec) {
        return of(dataset, selectedCaseIds, target, evaluatorSpecs, primaryEvaluatorIds,
                metricPlan, gateSpec, 300, 0, 1, null, "");
    }

    /**
     * 构造执行清单.
     *
     * @param dataset 已发布数据集版本
     * @param selectedCaseIds 选用用例 id(可空;非空时须非空白/唯一/已知)
     * @param target 目标快照
     * @param evaluatorSpecs 评测器定义列表(至少一个,id 唯一)
     * @param primaryEvaluatorIds 主评测器 id 列表(至少一个,引用已知)
     * @param metricPlan 指标方案
     * @param gateSpec 门禁配置
     * @param timeoutSeconds 超时秒数(大于 0)
     * @param maxRetries 重试上限(非负)
     * @param maxParallelCases 用例并行度(至少 1)
     * @param createdAt 创建时间(可空,取当前 UTC)
     * @param manifestSha256 清单摘要(空则自动计算,提供则须一致)
     * @return 清单实例
     */
    public static RunManifest of(DatasetVersion dataset, List<String> selectedCaseIds,
            TargetSnapshot target, List<EvaluatorSpec> evaluatorSpecs,
            List<String> primaryEvaluatorIds, MetricPlan metricPlan, ReleaseGateSpec gateSpec,
            double timeoutSeconds, int maxRetries, int maxParallelCases,
            OffsetDateTime createdAt, String manifestSha256) {
        if (dataset == null) {
            throw new IllegalArgumentException("Field required");
        }
        if (target == null) {
            throw new IllegalArgumentException("Field required");
        }
        if (metricPlan == null) {
            throw new IllegalArgumentException("Field required");
        }
        if (gateSpec == null) {
            throw new IllegalArgumentException("Field required");
        }
        if (evaluatorSpecs == null || evaluatorSpecs.isEmpty()) {
            throw new IllegalArgumentException("Tuple should have at least 1 item after validation, not 0");
        }
        List<EvaluatorSpec> frozenSpecs =
                Collections.unmodifiableList(new ArrayList<>(evaluatorSpecs));
        if (primaryEvaluatorIds == null || primaryEvaluatorIds.isEmpty()) {
            throw new IllegalArgumentException("Tuple should have at least 1 item after validation, not 0");
        }
        for (String id : primaryEvaluatorIds) {
            if (DomainValidations.isBlank(id)) {
                throw new IllegalArgumentException("primary_evaluator_ids must not contain blank values");
            }
        }
        Set<String> primarySet = new HashSet<>(primaryEvaluatorIds);
        if (primarySet.size() != primaryEvaluatorIds.size()) {
            throw new IllegalArgumentException("primary_evaluator_ids must be unique");
        }
        List<String> frozenPrimary =
                Collections.unmodifiableList(new ArrayList<>(primaryEvaluatorIds));
        List<String> frozenSelected = selectedCaseIds == null
                ? null : Collections.unmodifiableList(new ArrayList<>(selectedCaseIds));
        if (!(timeoutSeconds > 0)) {
            throw new IllegalArgumentException("Input should be greater than 0");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Input should be greater than or equal to 0");
        }
        if (maxParallelCases < 1) {
            throw new IllegalArgumentException("Input should be greater than or equal to 1");
        }
        OffsetDateTime created = DomainValidations.normalizeUtc(
                createdAt == null ? DomainValidations.utcNow() : createdAt,
                "RunManifest created_at");
        if (manifestSha256 != null && !manifestSha256.isEmpty()) {
            DomainValidations.requireSha256(manifestSha256, "manifest_sha256");
        }

        if (dataset.status() != DatasetVersionStatus.PUBLISHED) {
            throw new IllegalArgumentException("RunManifest requires a published DatasetVersion");
        }
        if (frozenSelected != null) {
            if (frozenSelected.isEmpty()) {
                throw new IllegalArgumentException("selected_case_ids must contain at least one Case id");
            }
            for (String caseId : frozenSelected) {
                if (DomainValidations.isBlank(caseId)) {
                    throw new IllegalArgumentException("selected_case_ids must not contain blank values");
                }
            }
            Set<String> selectedSet = new HashSet<>(frozenSelected);
            if (selectedSet.size() != frozenSelected.size()) {
                throw new IllegalArgumentException("selected_case_ids must not contain duplicates");
            }
            Set<String> knownCaseIds = new HashSet<>();
            for (Case item : dataset.cases()) {
                knownCaseIds.add(item.id());
            }
            Set<String> unknown = new HashSet<>();
            for (String caseId : frozenSelected) {
                if (!knownCaseIds.contains(caseId)) {
                    unknown.add(caseId);
                }
            }
            if (!unknown.isEmpty()) {
                List<String> sorted = new ArrayList<>(unknown);
                Collections.sort(sorted);
                throw new IllegalArgumentException(
                        "selected_case_ids reference unknown Cases: " + join(", ", sorted));
            }
        }

        Set<String> evaluatorIds = new HashSet<>();
        for (EvaluatorSpec spec : frozenSpecs) {
            if (!evaluatorIds.add(spec.id())) {
                throw new IllegalArgumentException(
                        "Evaluator ids must be unique within a RunManifest");
            }
        }
        Set<String> unknownPrimary = new HashSet<>();
        for (String id : frozenPrimary) {
            if (!evaluatorIds.contains(id)) {
                unknownPrimary.add(id);
            }
        }
        if (!unknownPrimary.isEmpty()) {
            List<String> sorted = new ArrayList<>(unknownPrimary);
            Collections.sort(sorted);
            throw new IllegalArgumentException(
                    "primary_evaluator_ids reference unknown Evaluators: " + join(", ", sorted));
        }

        String expectedHash = ContentSha256.of(hashPayload(dataset, frozenSelected, target,
                frozenSpecs, frozenPrimary, metricPlan, gateSpec, timeoutSeconds, maxRetries,
                maxParallelCases));
        String effectiveHash;
        if (manifestSha256 != null && !manifestSha256.isEmpty()) {
            if (!manifestSha256.equals(expectedHash)) {
                throw new IllegalArgumentException("RunManifest content hash mismatch");
            }
            effectiveHash = manifestSha256;
        } else {
            effectiveHash = expectedHash;
        }

        return new RunManifest(dataset, frozenSelected, target, frozenSpecs, frozenPrimary,
                metricPlan, gateSpec, timeoutSeconds, maxRetries, maxParallelCases, created,
                effectiveHash);
    }

    /**
     * 从 payload 解析执行清单.
     *
     * @param payload payload 对象
     * @return 清单实例
     */
    public static RunManifest fromPayload(Map<String, Object> payload) {
        return of(DatasetVersion.fromPayload(PayloadValues.requiredMap(payload, "dataset")),
                PayloadValues.optionalStringList(payload, "selected_case_ids"),
                TargetSnapshot.fromPayload(PayloadValues.requiredMap(payload, "target")),
                parseEvaluatorSpecs(payload),
                PayloadValues.requiredStringList(payload, "primary_evaluator_ids"),
                MetricPlan.fromPayload(PayloadValues.requiredMap(payload, "metric_plan")),
                ReleaseGateSpec.fromPayload(PayloadValues.requiredMap(payload, "gate_spec")),
                PayloadValues.doubleOrDefault(payload, "timeout_seconds", 300),
                intOrDefault(payload, "max_retries", 0),
                intOrDefault(payload, "max_parallel_cases", 1),
                PayloadValues.optionalOffsetDateTime(payload, "created_at"),
                PayloadValues.stringOrDefault(payload, "manifest_sha256", ""));
    }

    private static List<EvaluatorSpec> parseEvaluatorSpecs(Map<String, Object> payload) {
        List<Object> rawSpecs = PayloadValues.requiredList(payload, "evaluator_specs");
        List<EvaluatorSpec> parsed = new ArrayList<>(rawSpecs.size());
        for (Object item : rawSpecs) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Input should be a valid dictionary");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> specPayload = (Map<String, Object>) item;
            parsed.add(EvaluatorSpec.fromPayload(specPayload));
        }
        return parsed;
    }

    private static int intOrDefault(Map<String, Object> payload, String field, int defaultValue) {
        Integer value = PayloadValues.optionalInteger(payload, field);
        return value == null ? defaultValue : value;
    }

    private static Map<String, Object> hashPayload(DatasetVersion dataset,
            List<String> selectedCaseIds, TargetSnapshot target, List<EvaluatorSpec> specs,
            List<String> primaryEvaluatorIds, MetricPlan metricPlan, ReleaseGateSpec gateSpec,
            double timeoutSeconds, int maxRetries, int maxParallelCases) {
        Map<String, Object> datasetProjection = new LinkedHashMap<>();
        datasetProjection.put("id", dataset.id());
        datasetProjection.put("dataset_id", dataset.datasetId());
        datasetProjection.put("version", dataset.version());
        datasetProjection.put("content_sha256", dataset.contentSha256());

        Map<String, Object> targetProjection = new LinkedHashMap<>();
        targetProjection.put("ref", target.ref().toPayload());
        targetProjection.put("content_sha256", target.contentSha256());

        List<Object> evaluatorProjections = new ArrayList<>(specs.size());
        for (EvaluatorSpec spec : specs) {
            Map<String, Object> projection = new LinkedHashMap<>();
            projection.put("id", spec.id());
            projection.put("version", spec.version());
            projection.put("content_sha256", spec.contentSha256());
            evaluatorProjections.add(projection);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataset", datasetProjection);
        payload.put("selected_case_ids", selectedCaseIds);
        payload.put("target", targetProjection);
        payload.put("evaluators", evaluatorProjections);
        payload.put("primary_evaluator_ids", primaryEvaluatorIds);
        payload.put("metric_plan", metricPlan.toPayload());
        payload.put("gate_spec", gateSpec.toPayload());
        payload.put("timeout_seconds", timeoutSeconds);
        payload.put("max_retries", maxRetries);
        payload.put("max_parallel_cases", maxParallelCases);
        return payload;
    }

    private static String join(String separator, List<String> values) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                buffer.append(separator);
            }
            buffer.append(values.get(i));
        }
        return buffer.toString();
    }

    public DatasetVersion dataset() {
        return dataset;
    }

    public List<String> selectedCaseIds() {
        return selectedCaseIds;
    }

    public TargetSnapshot target() {
        return target;
    }

    public List<EvaluatorSpec> evaluatorSpecs() {
        return evaluatorSpecs;
    }

    public List<String> primaryEvaluatorIds() {
        return primaryEvaluatorIds;
    }

    public MetricPlan metricPlan() {
        return metricPlan;
    }

    public ReleaseGateSpec gateSpec() {
        return gateSpec;
    }

    public double timeoutSeconds() {
        return timeoutSeconds;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public int maxParallelCases() {
        return maxParallelCases;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public String manifestSha256() {
        return manifestSha256;
    }

    /**
     * 选用用例(无选择时返回数据集全量,否则按选择顺序).
     *
     * @return 用例列表
     */
    public List<Case> executionCases() {
        if (selectedCaseIds == null) {
            return dataset.cases();
        }
        Map<String, Case> casesById = new LinkedHashMap<>();
        for (Case item : dataset.cases()) {
            casesById.put(item.id(), item);
        }
        List<Case> selected = new ArrayList<>(selectedCaseIds.size());
        for (String caseId : selectedCaseIds) {
            selected.add(casesById.get(caseId));
        }
        return Collections.unmodifiableList(selected);
    }

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataset", dataset.toPayload());
        payload.put("selected_case_ids", selectedCaseIds);
        payload.put("target", target.toPayload());
        List<Object> specPayloads = new ArrayList<>(evaluatorSpecs.size());
        for (EvaluatorSpec spec : evaluatorSpecs) {
            specPayloads.add(spec.toPayload());
        }
        payload.put("evaluator_specs", specPayloads);
        payload.put("primary_evaluator_ids", primaryEvaluatorIds);
        payload.put("metric_plan", metricPlan.toPayload());
        payload.put("gate_spec", gateSpec.toPayload());
        payload.put("timeout_seconds", timeoutSeconds);
        payload.put("max_retries", maxRetries);
        payload.put("max_parallel_cases", maxParallelCases);
        payload.put("created_at", DomainValidations.isoFormat(createdAt));
        payload.put("manifest_sha256", manifestSha256);
        return payload;
    }
}
