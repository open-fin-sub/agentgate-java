package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.UserContext;
import com.abchina.llmalf.agentgate.common.UserContextHolder;
import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.model.evaluator.CombinationPolicy;
import com.abchina.llmalf.agentgate.domain.model.evaluator.Evaluator;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorDraft;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorKind;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorRef;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSeverity;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSource;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.logic.EvaluatorCatalog;
import com.abchina.llmalf.agentgate.logic.EvaluatorLogic;
import com.abchina.llmalf.agentgate.logic.EvaluatorVersioning;
import com.abchina.llmalf.agentgate.service.vo.DraftDefinitionRequest;
import org.springframework.stereotype.Service;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 评测器管理服务.
 *
 * <p>对齐 Python application/evaluator_management.py 的目录面:
 * 内置目录合并、404/409/422 语义分类(对齐 _raise_catalog_error)。</p>
 */
@Service
public class EvaluatorServiceImpl {

    private final EvaluatorLogic evaluatorLogic;

    public EvaluatorServiceImpl(EvaluatorLogic evaluatorLogic) {
        this.evaluatorLogic = evaluatorLogic;
    }

    /**
     * 列出评测器(内置在前,用户按 updated_at 降序;含冲突检测).
     *
     * @param includeDisabled 是否包含禁用
     * @return 身份列表
     */
    public List<Evaluator> listEvaluators(boolean includeDisabled) {
        List<Evaluator> users = evaluatorLogic.listEvaluators(includeDisabled, teamId());
        for (Evaluator user : users) {
            if (EvaluatorCatalog.isBuiltin(user.id())) {
                throw conflict(
                        "user Evaluator conflicts with built-in: " + user.id());
            }
        }
        List<Evaluator> result = new ArrayList<>();
        for (Map.Entry<String, Evaluator> entry :
                builtinOrderEntries()) {
            result.add(entry.getValue());
        }
        result.addAll(users);
        return result;
    }

    /**
     * 查询评测器身份(内置优先).
     *
     * @param evaluatorId 评测器 id
     * @return 身份
     */
    public Evaluator getEvaluator(String evaluatorId) {
        Evaluator builtin = EvaluatorCatalog.builtinEvaluator(evaluatorId);
        if (builtin != null) {
            return builtin;
        }
        return userEvaluator(evaluatorId);
    }

    /**
     * 创建评测器与初始草稿.
     *
     * @param name 名称
     * @param description 描述
     * @param definition 草稿定义
     * @return 身份与草稿
     */
    public Map<String, Object> createEvaluator(String name, String description,
            DraftDefinitionRequest definition) {
        UserContext context = UserContextHolder.current();
        Evaluator evaluator = Evaluator.of(UUID.randomUUID().toString(), name.trim(),
                description == null ? "" : description.trim(), EvaluatorSource.USER, false,
                null, null, context.userTeamId(), context.userId(), context.userName());
        EvaluatorDraft draft = buildDraft(evaluator, null, definition, context);
        evaluatorLogic.saveEvaluatorWithDraft(evaluator, draft);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evaluator", evaluator.toPayload());
        result.put("draft", draft.toPayload());
        return result;
    }

    /**
     * 更新评测器(字段级校验在 Controller).
     *
     * @param evaluatorId 评测器 id
     * @param name 名称(可空)
     * @param description 描述(可空)
     * @param enabled 启用(可空)
     * @return 更新后身份
     */
    public Evaluator updateEvaluator(String evaluatorId, String name, String description,
            Boolean enabled) {
        Evaluator evaluator = userEvaluator(evaluatorId);
        if (Boolean.TRUE.equals(enabled)
                && evaluatorLogic.getLatestEvaluatorVersion(evaluatorId, teamId()) == null) {
            throw conflict("unpublished Evaluator cannot be enabled");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                new LinkedHashMap<>((Map<String, Object>) evaluator.toPayload());
        if (name != null) {
            payload.put("name", name.trim());
        }
        if (description != null) {
            payload.put("description", description.trim());
        }
        if (enabled != null) {
            payload.put("enabled", enabled);
        }
        payload.put("updated_at", DomainValidations.isoFormat(DomainValidations.utcNow()));
        Evaluator updated = Evaluator.fromPayload(payload);
        evaluatorLogic.saveEvaluator(updated);
        return updated;
    }

    /**
     * 删除未发布评测器.
     *
     * @param evaluatorId 评测器 id
     */
    public void deleteEvaluator(String evaluatorId) {
        userEvaluator(evaluatorId);
        if (evaluatorLogic.getLatestEvaluatorVersion(evaluatorId, teamId()) != null) {
            throw conflict("published Evaluator cannot be deleted; disable it instead");
        }
        evaluatorLogic.deleteUnpublishedEvaluator(evaluatorId, teamId());
    }

    /**
     * 查询当前草稿.
     *
     * @param evaluatorId 评测器 id
     * @return 草稿
     */
    public EvaluatorDraft getDraft(String evaluatorId) {
        userEvaluator(evaluatorId);
        EvaluatorDraft draft = evaluatorLogic.getEvaluatorDraft(evaluatorId, teamId());
        if (draft == null) {
            throw notFound("Evaluator has no active draft: " + evaluatorId);
        }
        return draft;
    }

    /**
     * 创建草稿(默认克隆最新发布版).
     *
     * @param evaluatorId 评测器 id
     * @param basedOnVersion 基线版本(可空)
     * @return 草稿
     */
    public EvaluatorDraft createDraft(String evaluatorId, String basedOnVersion) {
        Evaluator evaluator = userEvaluator(evaluatorId);
        if (evaluatorLogic.getEvaluatorDraft(evaluatorId, teamId()) != null) {
            throw conflict("Evaluator already has an active draft");
        }
        EvaluatorSpec base = basedOnVersion != null
                ? getVersion(evaluatorId, basedOnVersion)
                : evaluatorLogic.getLatestEvaluatorVersion(evaluatorId, teamId());
        if (base == null) {
            throw conflict("Evaluator without a publication cannot clone a draft");
        }
        EvaluatorDraft draft = EvaluatorVersioning.cloneToDraft(evaluator, base,
                UUID.randomUUID().toString(), DomainValidations.utcNow());
        evaluatorLogic.saveEvaluatorDraft(draft);
        return draft;
    }

    /**
     * 整体替换草稿.
     *
     * @param evaluatorId 评测器 id
     * @param definition 草稿定义
     * @return 新草稿
     */
    public EvaluatorDraft replaceDraft(String evaluatorId, DraftDefinitionRequest definition) {
        EvaluatorDraft draft = getDraft(evaluatorId);
        EvaluatorDraft updated = EvaluatorVersioning.replaceDraft(draft,
                DomainValidations.utcNow(),
                kind(definition.getKind()), definition.getDimension(),
                definition.getMetric(), severity(definition.getSeverity()),
                definition.getImplementationId(),
                version(definition.getImplementationVersion()),
                definition.getConfig(), refs(definition.getChildren()),
                combination(definition.getCombination()));
        evaluatorLogic.saveEvaluatorDraft(updated);
        return updated;
    }

    /**
     * 丢弃草稿.
     *
     * @param evaluatorId 评测器 id
     */
    public void discardDraft(String evaluatorId) {
        EvaluatorDraft draft = getDraft(evaluatorId);
        evaluatorLogic.deleteEvaluatorDraft(evaluatorId, draft.id(), teamId());
    }

    /**
     * 列出发布版本(内置单版本).
     *
     * @param evaluatorId 评测器 id
     * @return 版本列表
     */
    public List<EvaluatorSpec> listVersions(String evaluatorId) {
        EvaluatorSpec builtin = EvaluatorCatalog.builtinSpec(evaluatorId);
        if (builtin != null) {
            return Collections.singletonList(builtin);
        }
        userEvaluator(evaluatorId);
        return evaluatorLogic.listEvaluatorVersions(evaluatorId, teamId());
    }

    /**
     * 查询发布版本.
     *
     * @param evaluatorId 评测器 id
     * @param version 版本字符串
     * @return 版本
     */
    public EvaluatorSpec getVersion(String evaluatorId, String version) {
        EvaluatorSpec builtin = EvaluatorCatalog.builtinSpec(evaluatorId);
        if (builtin != null) {
            if (builtin.version().equals(version)) {
                return builtin;
            }
            throw notFound("unknown Evaluator version: " + evaluatorId + "@" + version);
        }
        userEvaluator(evaluatorId);
        EvaluatorSpec published = evaluatorLogic.getEvaluatorVersion(evaluatorId, version,
                teamId());
        if (published == null) {
            throw notFound("unknown Evaluator version: " + evaluatorId + "@" + version);
        }
        return published;
    }

    /**
     * 发布草稿(支持性/hybrid 校验 + 版本连续性).
     *
     * @param evaluatorId 评测器 id
     * @return 发布规格
     */
    public EvaluatorSpec publishDraft(String evaluatorId) {
        Evaluator evaluator = userEvaluator(evaluatorId);
        EvaluatorDraft draft = getDraft(evaluatorId);
        EvaluatorSpec latest = evaluatorLogic.getLatestEvaluatorVersion(evaluatorId, teamId());
        int nextVersion = latest == null ? 1
                : (int) EvaluatorVersioning.parseVersion(latest.version()) + 1;
        EvaluatorSpec published = EvaluatorVersioning.publish(evaluator, draft, nextVersion);
        try {
            EvaluatorCatalog.validateSupportedSpec(published);
            List<EvaluatorKind> childKinds = new ArrayList<>();
            for (EvaluatorRef child : published.children()) {
                childKinds.add(getVersion(child.evaluatorId(),
                        child.evaluatorVersion()).kind());
            }
            EvaluatorCatalog.validateHybridChildren(published, childKinds);
        } catch (IllegalArgumentException e) {
            throw new AgentException(422, e.getMessage());
        }
        try {
            evaluatorLogic.publishEvaluatorDraft(draft.id(), published);
        } catch (IllegalArgumentException e) {
            throw conflict("Evaluator draft changed during publication");
        }
        return published;
    }

    private EvaluatorDraft buildDraft(Evaluator evaluator, String basedOnVersion,
            DraftDefinitionRequest definition, UserContext context) {
        try {
            return EvaluatorVersioning.createDraft(evaluator, UUID.randomUUID().toString(),
                    DomainValidations.utcNow(), kind(definition.getKind()),
                    definition.getDimension(), definition.getMetric(),
                    severity(definition.getSeverity()), definition.getImplementationId(),
                    version(definition.getImplementationVersion()), definition.getConfig(),
                    refs(definition.getChildren()), combination(definition.getCombination()),
                    context.userTeamId(), context.userId(), context.userName());
        } catch (IllegalArgumentException e) {
            throw new AgentException(422, e.getMessage());
        }
    }

    private Evaluator userEvaluator(String evaluatorId) {
        if (EvaluatorCatalog.isBuiltin(evaluatorId)) {
            throw conflict("built-in Evaluator is read-only: " + evaluatorId);
        }
        Evaluator evaluator = evaluatorLogic.getEvaluator(evaluatorId, teamId());
        if (evaluator == null) {
            throw notFound("unknown Evaluator: " + evaluatorId);
        }
        return evaluator;
    }

    private static EvaluatorKind kind(String wire) {
        return EvaluatorKind.fromWireValue(wire == null ? "rule" : wire);
    }

    private static EvaluatorSeverity severity(String wire) {
        return EvaluatorSeverity.fromWireValue(wire == null ? "standard" : wire);
    }

    private static String version(String value) {
        return value == null ? "1" : value;
    }

    private static CombinationPolicy combination(String wire) {
        return wire == null ? null : CombinationPolicy.fromWireValue(wire);
    }

    private static List<EvaluatorRef> refs(List<Map<String, Object>> children) {
        List<EvaluatorRef> parsed = new ArrayList<>();
        if (children != null) {
            for (Map<String, Object> child : children) {
                parsed.add(EvaluatorRef.fromPayload(child));
            }
        }
        return parsed;
    }

    private static List<Map.Entry<String, Evaluator>> builtinOrderEntries() {
        List<Map.Entry<String, Evaluator>> ordered = new ArrayList<>();
        for (EvaluatorSpec spec : EvaluatorCatalog.builtinSpecs()) {
            ordered.add(new SimpleEntry<>(spec.id(),
                    EvaluatorCatalog.builtinEvaluator(spec.id())));
        }
        return ordered;
    }

    private static String teamId() {
        return UserContextHolder.current().userTeamId();
    }

    private static AgentException notFound(String message) {
        return new AgentException(404, message);
    }

    private static AgentException conflict(String message) {
        return new AgentException(409, message);
    }
}
