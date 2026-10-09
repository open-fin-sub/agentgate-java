package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.UserContextHolder;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.cases.CaseTurn;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.evaluator.Evaluator;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorKind;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorRef;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.domain.model.expectation.Expectation;
import com.abchina.llmalf.agentgate.logic.EvaluatorCatalog;
import com.abchina.llmalf.agentgate.logic.EvaluatorLogic;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测器选择与计划校验.
 *
 * <p>对齐 Python EvaluatorManagement.select/select_versions/_add_selectable/
 * validate_plan:内置优先、用户已发布版本回退(团队隔离)、Hybrid 子级自动纳入、
 * 引用唯一性、metric/dimension 归属、内容哈希复核。</p>
 */
@Service
public class EvaluatorSelection {

    private final EvaluatorLogic evaluatorLogic;

    public EvaluatorSelection(EvaluatorLogic evaluatorLogic) {
        this.evaluatorLogic = evaluatorLogic;
    }

    /**
     * 按 id 选择(null 为内置全套;重复 id 幂等;hybrid 子级自动纳入).
     *
     * @param evaluatorIds id 列表
     * @return 规格列表
     */
    public List<EvaluatorSpec> select(List<String> evaluatorIds) {
        if (evaluatorIds == null) {
            return EvaluatorCatalog.builtinSpecs();
        }
        if (evaluatorIds.isEmpty()) {
            throw new AgentException(422, "at least one Evaluator is required");
        }
        Map<String, EvaluatorSpec> selected = new LinkedHashMap<>();
        for (String evaluatorId : evaluatorIds) {
            addSelectable(evaluatorId, null, selected);
        }
        return new ArrayList<>(selected.values());
    }

    /**
     * 按精确引用选择(id+版本,引用必须唯一).
     *
     * @param refs 引用列表
     * @return 规格列表
     */
    public List<EvaluatorSpec> selectVersions(List<EvaluatorRef> refs) {
        if (refs == null || refs.isEmpty()) {
            throw new AgentException(422, "at least one Evaluator is required");
        }
        List<String> evaluatorIds = new ArrayList<>(refs.size());
        for (EvaluatorRef ref : refs) {
            evaluatorIds.add(ref.evaluatorId());
        }
        if (new HashSet<>(evaluatorIds).size() != evaluatorIds.size()) {
            throw new AgentException(422, "Evaluator references must be unique");
        }
        Map<String, EvaluatorSpec> selected = new LinkedHashMap<>();
        for (EvaluatorRef ref : refs) {
            addSelectable(ref.evaluatorId(), ref.evaluatorVersion(), selected);
        }
        return new ArrayList<>(selected.values());
    }

    private void addSelectable(String evaluatorId, String exactVersion,
            Map<String, EvaluatorSpec> selected) {
        EvaluatorSpec existing = selected.get(evaluatorId);
        if (existing != null) {
            if (exactVersion != null && !existing.version().equals(exactVersion)) {
                throw new AgentException(422, evaluatorId
                        + " requires both evaluator version "
                        + existing.version() + " and " + exactVersion);
            }
            return;
        }
        EvaluatorSpec spec = resolveSpec(evaluatorId, exactVersion);
        selected.put(evaluatorId, spec);
        for (EvaluatorRef child : spec.children()) {
            addSelectable(child.evaluatorId(), child.evaluatorVersion(), selected);
        }
    }

    private EvaluatorSpec resolveSpec(String evaluatorId, String exactVersion) {
        EvaluatorSpec builtin = EvaluatorCatalog.builtinSpec(evaluatorId);
        if (builtin != null) {
            if (exactVersion == null || builtin.version().equals(exactVersion)) {
                return builtin;
            }
            throw new AgentException(422,
                    "unknown Evaluator version: " + evaluatorId + "@" + exactVersion);
        }
        String teamId = UserContextHolder.current().userTeamId();
        Evaluator evaluator = evaluatorLogic.getEvaluator(evaluatorId, teamId);
        if (evaluator == null) {
            throw new AgentException(422, "unknown Evaluator: " + evaluatorId);
        }
        if (!evaluator.enabled()) {
            throw new AgentException(422,
                    "disabled Evaluator cannot be selected: " + evaluatorId);
        }
        EvaluatorSpec spec;
        if (exactVersion != null) {
            spec = evaluatorLogic.getEvaluatorVersion(evaluatorId, exactVersion, teamId);
            if (spec == null) {
                throw new AgentException(422,
                        "unknown Evaluator version: " + evaluatorId + "@" + exactVersion);
            }
        } else {
            spec = evaluatorLogic.getLatestEvaluatorVersion(evaluatorId, teamId);
            if (spec == null) {
                throw new AgentException(422,
                        "unpublished Evaluator cannot be selected: " + evaluatorId);
            }
        }
        return spec;
    }

    /**
     * 校验评测计划(唯一性、内容哈希复核、metric/dimension 归属、Hybrid 子约束).
     *
     * @param dataset 数据集版本
     * @param specs 已选规格
     */
    public void validatePlan(DatasetVersion dataset, List<EvaluatorSpec> specs) {
        if (specs.isEmpty()) {
            throw new AgentException(422, "at least one Evaluator is required");
        }
        Map<String, EvaluatorSpec> byId = new LinkedHashMap<>();
        for (EvaluatorSpec spec : specs) {
            if (byId.put(spec.id(), spec) != null) {
                throw new AgentException(422, "evaluator IDs must be unique");
            }
        }
        Map<String, String> metricDimensions = new LinkedHashMap<>();
        for (EvaluatorSpec spec : specs) {
            EvaluatorSpec available = resolveSpec(spec.id(), spec.version());
            if (!spec.contentSha256().equals(available.contentSha256())) {
                throw new AgentException(422, "Evaluator content mismatch: " + spec.id());
            }
            EvaluatorCatalog.validateSupportedSpec(spec);
            String previous = metricDimensions.putIfAbsent(spec.metric(),
                    spec.dimension());
            if (previous != null && !previous.equals(spec.dimension())) {
                throw new AgentException(422,
                        "metric '" + spec.metric() + "' cannot belong to both '"
                                + previous + "' and '" + spec.dimension() + "'");
            }
            List<EvaluatorKind> childKinds = new ArrayList<>(spec.children().size());
            for (EvaluatorRef child : spec.children()) {
                EvaluatorSpec childSpec = byId.get(child.evaluatorId());
                if (childSpec == null) {
                    throw new AgentException(422,
                            "unknown Evaluator: " + child.evaluatorId());
                }
                if (!child.evaluatorVersion().equals(childSpec.version())) {
                    throw new AgentException(422,
                            child.evaluatorId() + " requires version "
                                    + child.evaluatorVersion() + ", not "
                                    + childSpec.version());
                }
                childKinds.add(childSpec.kind());
            }
            EvaluatorCatalog.validateHybridChildren(spec, childKinds);
        }
        for (Case caseItem : dataset.cases()) {
            for (CaseTurn turn
                    : caseItem.turns()) {
                for (Expectation expectation
                        : turn.expectations()) {
                    // JSON Schema 结构与策略 id 合法性校验归 Python 侧执行链;
                    // Java 端点面仅透传(demo 内置目录固化合法)
                }
            }
        }
    }
}
