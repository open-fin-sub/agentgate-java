package com.abchina.llmalf.agentgate.domain.model.evaluator;

import com.abchina.llmalf.agentgate.domain.DomainValidations;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评测器定义共享校验.
 *
 * <p>对齐 Python domain/evaluator.py::_validate_definition:
 * LLM_JUDGE 配置结构与 Hybrid 组合规则,Draft/Spec 共用。</p>
 */
final class EvaluatorDefinitions {

    private EvaluatorDefinitions() {
    }

    static void validate(EvaluatorKind kind, Map<String, Object> config,
            List<EvaluatorRef> children, CombinationPolicy combination) {
        if (kind == EvaluatorKind.LLM_JUDGE) {
            validateLlmJudgeConfig(config);
        }

        Set<String> childKeys = new HashSet<>();
        for (EvaluatorRef child : children) {
            if (!childKeys.add(child.evaluatorId() + "\n" + child.evaluatorVersion())) {
                throw new IllegalArgumentException("Hybrid child Evaluator references must be unique");
            }
        }

        if (kind != EvaluatorKind.HYBRID) {
            if (!children.isEmpty() || combination != null) {
                throw new IllegalArgumentException("non-Hybrid Evaluator cannot define composition");
            }
            return;
        }

        if (children.size() < 2 || combination == null) {
            throw new IllegalArgumentException(
                    "Hybrid Evaluator requires at least two children and a combination");
        }
        boolean weighted = combination == CombinationPolicy.WEIGHTED_SCORE;
        if (weighted) {
            for (EvaluatorRef child : children) {
                if (child.weight() == null) {
                    throw new IllegalArgumentException(
                            "weighted_score requires every child to have a weight");
                }
            }
        } else {
            for (EvaluatorRef child : children) {
                if (child.weight() != null) {
                    throw new IllegalArgumentException(
                            "only weighted_score children may define weights");
                }
            }
        }
    }

    private static void validateLlmJudgeConfig(Map<String, Object> config) {
        Object model = config.get("model");
        if (!(model instanceof Map)) {
            throw new IllegalArgumentException("LLM Judge config requires a model object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> modelConfig = (Map<String, Object>) model;
        for (String fieldName : new String[] {"provider_id", "model_id"}) {
            Object value = modelConfig.get(fieldName);
            if (!(value instanceof String)) {
                throw new IllegalArgumentException(
                        "LLM Judge config model." + fieldName + " must be a string");
            }
            DomainValidations.requireNonBlank((String) value,
                    "LLM Judge config model." + fieldName);
        }
        Object credentialRef = modelConfig.get("credential_ref");
        if (credentialRef != null) {
            if (!(credentialRef instanceof String)) {
                throw new IllegalArgumentException(
                        "LLM Judge config model.credential_ref must be a string");
            }
            DomainValidations.requireNonBlank((String) credentialRef,
                    "LLM Judge config model.credential_ref");
        }
    }
}
