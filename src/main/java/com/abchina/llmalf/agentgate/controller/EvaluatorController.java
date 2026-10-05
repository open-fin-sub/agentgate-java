package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.ResponseBase;
import com.abchina.llmalf.agentgate.domain.model.evaluator.Evaluator;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorDraft;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.service.impl.EvaluatorServiceImpl;
import com.abchina.llmalf.agentgate.service.vo.DraftDefinitionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测器管理端点.
 *
 * <p>对齐 Python server/routes/evaluators.py 的 12 个端点:
 * summary/detail 组装、409 冲突语义、字段级更新校验。</p>
 */
@RestController
@RequestMapping("/api/evaluators")
public class EvaluatorController {

    private final EvaluatorServiceImpl evaluatorService;

    public EvaluatorController(EvaluatorServiceImpl evaluatorService) {
        this.evaluatorService = evaluatorService;
    }

    /**
     * 评测器摘要列表.
     *
     * @param includeDisabled 是否包含禁用
     * @return 摘要列表
     */
    @GetMapping
    public ResponseBase<List<Map<String, Object>>> listEvaluators(
            @RequestParam(value = "include_disabled", required = false, defaultValue = "false")
            boolean includeDisabled) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Evaluator evaluator : evaluatorService.listEvaluators(includeDisabled)) {
            summaries.add(summary(evaluator));
        }
        return ResponseBase.success(summaries);
    }

    /**
     * 创建评测器与初始草稿.
     *
     * @param request 创建请求
     * @return 身份与草稿
     */
    @PostMapping
    public ResponseEntity<ResponseBase<Map<String, Object>>> createEvaluator(
            @RequestBody Map<String, Object> request) {
        requireFields(request, "name", "draft");
        @SuppressWarnings("unchecked")
        Map<String, Object> draftBody = (Map<String, Object>) request.get("draft");
        DraftDefinitionRequest definition = definitionOf(draftBody);
        Map<String, Object> result = evaluatorService.createEvaluator(
                String.valueOf(request.get("name")),
                request.get("description") == null ? ""
                        : String.valueOf(request.get("description")),
                definition);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseBase.success(result));
    }

    /**
     * 评测器详情(身份 + 最新版 + 草稿).
     *
     * @param evaluatorId 评测器 id
     * @return 详情
     */
    @GetMapping("/{evaluatorId}")
    public ResponseBase<Map<String, Object>> evaluatorDetail(
            @PathVariable("evaluatorId") String evaluatorId) {
        return ResponseBase.success(detail(evaluatorService.getEvaluator(evaluatorId)));
    }

    /**
     * 更新评测器元数据(字段级校验).
     *
     * @param evaluatorId 评测器 id
     * @param request 更新请求
     * @return 更新后身份
     */
    @PatchMapping("/{evaluatorId}")
    public ResponseBase<Map<String, Object>> updateEvaluator(
            @PathVariable("evaluatorId") String evaluatorId,
            @RequestBody Map<String, Object> request) {
        if (request.isEmpty()) {
            throw new AgentException(422, "Evaluator update must contain at least one field");
        }
        for (Map.Entry<String, Object> entry : request.entrySet()) {
            if (entry.getValue() == null) {
                throw new AgentException(422, "Evaluator update fields cannot be null");
            }
        }
        Evaluator updated = evaluatorService.updateEvaluator(evaluatorId,
                textOrNull(request, "name"), textOrNull(request, "description"),
                boolOrNull(request, "enabled"));
        return ResponseBase.success((Map<String, Object>) updated.toPayload());
    }

    /**
     * 删除未发布评测器.
     *
     * @param evaluatorId 评测器 id
     * @return 204
     */
    @DeleteMapping("/{evaluatorId}")
    public ResponseEntity<Void> deleteEvaluator(@PathVariable("evaluatorId") String evaluatorId) {
        evaluatorService.deleteEvaluator(evaluatorId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 版本列表.
     *
     * @param evaluatorId 评测器 id
     * @return 版本列表
     */
    @GetMapping("/{evaluatorId}/versions")
    public ResponseBase<List<Object>> listVersions(
            @PathVariable("evaluatorId") String evaluatorId) {
        List<Object> payloads = new ArrayList<>();
        for (EvaluatorSpec spec : evaluatorService.listVersions(evaluatorId)) {
            payloads.add(spec.toPayload());
        }
        return ResponseBase.success(payloads);
    }

    /**
     * 发布版本详情.
     *
     * @param evaluatorId 评测器 id
     * @param version 版本字符串
     * @return 版本
     */
    @GetMapping("/{evaluatorId}/versions/{version}")
    public ResponseBase<Object> evaluatorVersion(
            @PathVariable("evaluatorId") String evaluatorId,
            @PathVariable("version") String version) {
        return ResponseBase.success(
                (Object) evaluatorService.getVersion(evaluatorId, version).toPayload());
    }

    /**
     * 当前草稿.
     *
     * @param evaluatorId 评测器 id
     * @return 草稿
     */
    @GetMapping("/{evaluatorId}/drafts/current")
    public ResponseBase<Object> currentDraft(@PathVariable("evaluatorId") String evaluatorId) {
        return ResponseBase.success(
                (Object) evaluatorService.getDraft(evaluatorId).toPayload());
    }

    /**
     * 创建草稿.
     *
     * @param evaluatorId 评测器 id
     * @param request 创建请求(based_on_version 可空)
     * @return 草稿
     */
    @PostMapping("/{evaluatorId}/drafts")
    public ResponseEntity<ResponseBase<Object>> createDraft(
            @PathVariable("evaluatorId") String evaluatorId,
            @RequestBody(required = false) Map<String, Object> request) {
        String basedOn = request == null ? null : textOrNull(request, "based_on_version");
        EvaluatorDraft draft = evaluatorService.createDraft(evaluatorId, basedOn);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseBase.success((Object) draft.toPayload()));
    }

    /**
     * 整体替换草稿.
     *
     * @param evaluatorId 评测器 id
     * @param request 草稿定义
     * @return 新草稿
     */
    @PutMapping("/{evaluatorId}/drafts/current")
    public ResponseBase<Object> replaceDraft(@PathVariable("evaluatorId") String evaluatorId,
            @RequestBody DraftDefinitionRequest request) {
        EvaluatorDraft draft = evaluatorService.replaceDraft(evaluatorId, request);
        return ResponseBase.success((Object) draft.toPayload());
    }

    /**
     * 丢弃草稿.
     *
     * @param evaluatorId 评测器 id
     * @return 204
     */
    @DeleteMapping("/{evaluatorId}/drafts/current")
    public ResponseEntity<Void> discardDraft(
            @PathVariable("evaluatorId") String evaluatorId) {
        evaluatorService.discardDraft(evaluatorId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 发布草稿.
     *
     * @param evaluatorId 评测器 id
     * @return 发布规格
     */
    @PostMapping("/{evaluatorId}/drafts/publish")
    public ResponseBase<Object> publishDraft(@PathVariable("evaluatorId") String evaluatorId) {
        return ResponseBase.success(
                (Object) evaluatorService.publishDraft(evaluatorId).toPayload());
    }

    private Map<String, Object> detail(Evaluator evaluator) {
        List<EvaluatorSpec> versions = evaluatorService.listVersions(evaluator.id());
        EvaluatorDraft draft = null;
        if (!"builtin".equals(evaluator.source().wireValue())) {
            try {
                draft = evaluatorService.getDraft(evaluator.id());
            } catch (AgentException e) {
                if (e.getHttpStatus() != 404) {
                    throw e;
                }
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evaluator", evaluator.toPayload());
        payload.put("latest", versions.isEmpty() ? null : versions.get(0).toPayload());
        payload.put("draft", draft == null ? null : draft.toPayload());
        return payload;
    }

    private Map<String, Object> summary(Evaluator evaluator) {
        List<EvaluatorSpec> versions = evaluatorService.listVersions(evaluator.id());
        EvaluatorDraft draft = null;
        if (!"builtin".equals(evaluator.source().wireValue())) {
            try {
                draft = evaluatorService.getDraft(evaluator.id());
            } catch (AgentException e) {
                if (e.getHttpStatus() != 404) {
                    throw e;
                }
            }
        }
        EvaluatorSpec latest = versions.isEmpty() ? null : versions.get(0);
        Map<String, Object> definition = latest != null ? (Map<String, Object>) latest.toPayload()
                : (draft != null ? (Map<String, Object>) draft.toPayload() : null);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary =
                new LinkedHashMap<>((Map<String, Object>) evaluator.toPayload());
        // 列表投影排除用户身份字段(对齐 Python EvaluatorSummary)
        summary.remove("user_id");
        summary.remove("user_name");
        summary.remove("user_team_id");
        summary.put("latest_version", latest == null ? null : latest.version());
        summary.put("kind", definition == null ? null : definition.get("kind"));
        summary.put("dimension", definition == null ? null : definition.get("dimension"));
        summary.put("metric", definition == null ? null : definition.get("metric"));
        summary.put("severity", definition == null ? null : definition.get("severity"));
        summary.put("implementation_id",
                definition == null ? null : definition.get("implementation_id"));
        summary.put("implementation_version",
                definition == null ? null : definition.get("implementation_version"));
        summary.put("has_draft", draft != null);
        return summary;
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper SNAKE_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .setPropertyNamingStrategy(
                            com.fasterxml.jackson.databind.PropertyNamingStrategy.SNAKE_CASE);

    private static DraftDefinitionRequest definitionOf(Map<String, Object> body) {
        return SNAKE_MAPPER.convertValue(
                body == null ? new LinkedHashMap<String, Object>() : body,
                DraftDefinitionRequest.class);
    }

    private static void requireFields(Map<String, Object> body, String... fields) {
        for (String field : fields) {
            if (!body.containsKey(field)) {
                throw new AgentException(422, "Field required");
            }
        }
    }

    private static String textOrNull(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean boolOrNull(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }
}
