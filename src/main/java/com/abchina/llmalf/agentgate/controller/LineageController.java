package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.ApiErrors;
import com.abchina.llmalf.agentgate.common.PydanticErrors;
import com.abchina.llmalf.agentgate.common.ResponseBase;
import com.abchina.llmalf.agentgate.domain.model.target.TargetRef;
import com.abchina.llmalf.agentgate.domain.model.target.TargetType;
import com.abchina.llmalf.agentgate.service.impl.LineageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 血缘查询端点.
 *
 * <p>对齐 Python server/routes/lineage.py 的 6 端点;
 * unknown TargetDescriptor → 409,其余 404。</p>
 */
@RestController
public class LineageController {

    private static int pathVersion(PydanticErrors errors, String raw) {
        Integer parsed;
        try {
            parsed = Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            errors.intParsing("path", "version", raw);
            return -1;
        }
        if (parsed < 1) {
            errors.greaterEqual("path", "version", raw, 1);
            return -1;
        }
        return parsed;
    }

    private static int queryLimit(PydanticErrors errors, String raw) {
        if (raw == null) {
            return 50;
        }
        Integer parsed;
        try {
            parsed = Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            errors.intParsing("query", "limit", raw);
            return -1;
        }
        if (parsed < 1) {
            errors.greaterEqual("query", "limit", raw, 1);
            return -1;
        }
        if (parsed > 200) {
            errors.lessEqual("query", "limit", raw, 200);
            return -1;
        }
        return parsed;
    }

    private static TargetType targetTypeOf(PydanticErrors errors, String raw) {
        try {
            return TargetType.fromWireValue(raw);
        } catch (IllegalArgumentException e) {
            errors.custom("enum", "path", "target_type",
                    "Input should be 'agent' or 'skill'", raw,
                    java.util.Collections.singletonMap("expected",
                            "'agent' or 'skill'"));
            return null;
        }
    }

    private final LineageService lineageService;

    public LineageController(LineageService lineageService) {
        this.lineageService = lineageService;
    }

    /**
     * Run 血缘图.
     *
     * @param runId Run id
     * @return 图投影
     */
    @GetMapping("/api/runs/{runId}/lineage")
    public ResponseBase<Map<String, Object>> runLineage(@PathVariable("runId") String runId) {
        return ResponseBase.success(ApiErrors.notFound(() ->
                lineageService.runLineage(runId)));
    }

    /**
     * 数据集版本血缘图.
     *
     * @param datasetId 数据集 id
     * @param version 版本号
     * @param limit 上限
     * @return 图投影
     */
    @GetMapping("/api/datasets/{datasetId}/versions/{version}/lineage")
    public ResponseBase<Map<String, Object>> datasetLineage(
            @PathVariable("datasetId") String datasetId,
            @PathVariable("version") String version,
            @RequestParam(value = "limit", required = false) String limit) {
        PydanticErrors errors = new PydanticErrors();
        int versionValue = pathVersion(errors, version);
        int limitValue = queryLimit(errors, limit);
        errors.throwIfAny();
        return ResponseBase.success(ApiErrors.notFound(() ->
                lineageService.datasetLineage(datasetId, versionValue, limitValue)));
    }

    /**
     * 用例血缘图.
     *
     * @param datasetId 数据集 id
     * @param version 版本号
     * @param caseId 用例 id
     * @param limit 上限
     * @return 图投影
     */
    @GetMapping("/api/datasets/{datasetId}/versions/{version}/cases/{caseId}/lineage")
    public ResponseBase<Map<String, Object>> caseLineage(
            @PathVariable("datasetId") String datasetId,
            @PathVariable("version") String version,
            @PathVariable("caseId") String caseId,
            @RequestParam(value = "limit", required = false) String limit) {
        PydanticErrors errors = new PydanticErrors();
        int versionValue = pathVersion(errors, version);
        int limitValue = queryLimit(errors, limit);
        errors.throwIfAny();
        return ResponseBase.success(ApiErrors.notFound(() ->
                lineageService.caseLineage(datasetId, versionValue, caseId, limitValue)));
    }

    /**
     * 目标血缘图.
     *
     * @param sourceId 来源 id
     * @param targetType 目标类型
     * @param targetId 目标 id
     * @param version 版本
     * @param contentSha256 内容摘要(可空)
     * @param limit 上限
     * @return 图投影
     */
    @GetMapping("/api/targets/{sourceId}/{targetType}/{targetId}/versions/{version}/lineage")
    public ResponseBase<Map<String, Object>> targetLineage(
            @PathVariable("sourceId") String sourceId,
            @PathVariable("targetType") String targetType,
            @PathVariable("targetId") String targetId,
            @PathVariable("version") String version,
            @RequestParam(value = "content_sha256", required = false) String contentSha256,
            @RequestParam(value = "limit", required = false) String limit) {
        PydanticErrors errors = new PydanticErrors();
        TargetType type = targetTypeOf(errors, targetType);
        int limitValue = queryLimit(errors, limit);
        errors.throwIfAny();
        TargetRef ref = TargetRef.of(sourceId, type, targetId, version);
        return ResponseBase.success(ApiErrors.notFound(() ->
                lineageService.targetLineage(ref, contentSha256, limitValue)));
    }

    /**
     * 技能血缘图.
     *
     * @param sourceId 来源 id
     * @param skillId 技能 id
     * @param version 技能版本
     * @param contentSha256 内容摘要(可空)
     * @param limit 上限
     * @return 图投影
     */
    @GetMapping("/api/skills/{sourceId}/{skillId}/versions/{version}/lineage")
    public ResponseBase<Map<String, Object>> skillLineage(
            @PathVariable("sourceId") String sourceId,
            @PathVariable("skillId") String skillId,
            @PathVariable("version") String version,
            @RequestParam(value = "content_sha256", required = false) String contentSha256,
            @RequestParam(value = "limit", required = false) String limit) {
        PydanticErrors errors = new PydanticErrors();
        int limitValue = queryLimit(errors, limit);
        errors.throwIfAny();
        return ResponseBase.success(ApiErrors.notFound(() ->
                lineageService.skillLineage(sourceId, skillId, version, contentSha256,
                        limitValue)));
    }

    /**
     * 评测器血缘图.
     *
     * @param evaluatorId 评测器 id
     * @param version 版本
     * @param contentSha256 内容摘要(可空)
     * @param limit 上限
     * @return 图投影
     */
    @GetMapping("/api/evaluators/{evaluatorId}/versions/{version}/lineage")
    public ResponseBase<Map<String, Object>> evaluatorLineage(
            @PathVariable("evaluatorId") String evaluatorId,
            @PathVariable("version") String version,
            @RequestParam(value = "content_sha256", required = false) String contentSha256,
            @RequestParam(value = "limit", required = false) String limit) {
        PydanticErrors errors = new PydanticErrors();
        int limitValue = queryLimit(errors, limit);
        errors.throwIfAny();
        return ResponseBase.success(ApiErrors.notFound(() ->
                lineageService.evaluatorLineage(evaluatorId, version, contentSha256,
                        limitValue)));
    }
}
