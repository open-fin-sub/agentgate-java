package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.domain.model.evaluator.Evaluator;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorKind;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSeverity;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSource;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置评测器目录与实现白名单.
 *
 * <p>对齐 Python application/evaluator_management.py 的
 * _BUILTIN_EVALUATOR_SPECS/_BUILTIN_IMPLEMENTATIONS/_builtin_evaluators
 * 与 _validate_supported_spec/_validate_hybrid_children 校验;
 * LLM Judge(answer_quality)依赖运行时 judge client,按
 * mock+TODO 决策不在 Java 白名单(发布时报 unknown implementation)。</p>
 */
public final class EvaluatorCatalog {

    /** 内置目录固定时间戳(2026-09-08T00:00:00Z) */
    public static final OffsetDateTime BUILTIN_DEFINED_AT =
            OffsetDateTime.of(2026, 9, 8, 0, 0, 0, 0, ZoneOffset.UTC);

    private static final List<EvaluatorSpec> BUILTIN_SPECS = buildBuiltinSpecs();

    private static final Map<String, Evaluator> BUILTIN_EVALUATORS = buildBuiltinEvaluators();

    /** 实现白名单:implementation_id@version → kind */
    private static final Map<String, EvaluatorKind> IMPLEMENTATIONS = buildImplementations();

    private EvaluatorCatalog() {
    }

    /**
     * 内置规格列表.
     *
     * @return 7 个内置 spec
     */
    public static List<EvaluatorSpec> builtinSpecs() {
        return BUILTIN_SPECS;
    }

    /**
     * 内置身份按 id 查找.
     *
     * @param evaluatorId 评测器 id
     * @return 内置身份(可空)
     */
    public static Evaluator builtinEvaluator(String evaluatorId) {
        return BUILTIN_EVALUATORS.get(evaluatorId);
    }

    /**
     * 内置身份是否包含.
     *
     * @param evaluatorId 评测器 id
     * @return 包含为 true
     */
    public static boolean isBuiltin(String evaluatorId) {
        return BUILTIN_EVALUATORS.containsKey(evaluatorId);
    }

    /**
     * 内置 spec 按 id 查找.
     *
     * @param evaluatorId 评测器 id
     * @return 内置 spec(可空)
     */
    public static EvaluatorSpec builtinSpec(String evaluatorId) {
        for (EvaluatorSpec spec : BUILTIN_SPECS) {
            if (spec.id().equals(evaluatorId)) {
                return spec;
            }
        }
        return null;
    }

    /**
     * 校验发布规格的实现支持性(消息对齐 _validate_supported_spec).
     *
     * @param spec 发布规格
     */
    public static void validateSupportedSpec(EvaluatorSpec spec) {
        String key = spec.implementationId() + "@" + spec.implementationVersion();
        EvaluatorKind implementationKind = IMPLEMENTATIONS.get(key);
        if (implementationKind == null) {
            throw new IllegalArgumentException(
                    "unknown evaluator implementation: " + key);
        }
        if (implementationKind != spec.kind()) {
            throw new IllegalArgumentException(
                    spec.implementationId() + " implements " + implementationKind.wireValue()
                            + ", not " + spec.kind().wireValue());
        }
        if (spec.kind() == EvaluatorKind.RULE && !spec.config().isEmpty()) {
            throw new IllegalArgumentException("Rule Evaluator config must be empty");
        }
    }

    /**
     * 校验 Hybrid 子引用规则(消息对齐 _validate_hybrid_children).
     *
     * @param spec 发布规格
     * @param childKinds 子规格的 kind 列表(与 children 顺序一致)
     */
    public static void validateHybridChildren(EvaluatorSpec spec, List<EvaluatorKind> childKinds) {
        if (spec.kind() == EvaluatorKind.HYBRID) {
            for (EvaluatorKind childKind : childKinds) {
                if (childKind == EvaluatorKind.HYBRID) {
                    throw new IllegalArgumentException("nested Hybrid is not supported");
                }
            }
            boolean hasRule = false;
            boolean hasLlmJudge = false;
            for (EvaluatorKind childKind : childKinds) {
                if (childKind == EvaluatorKind.RULE) {
                    hasRule = true;
                }
                if (childKind == EvaluatorKind.LLM_JUDGE) {
                    hasLlmJudge = true;
                }
            }
            if (!hasRule || !hasLlmJudge) {
                throw new IllegalArgumentException(
                        "Hybrid requires Rule and LLM Judge children");
            }
        }
    }

    private static List<EvaluatorSpec> buildBuiltinSpecs() {
        List<EvaluatorSpec> specs = new ArrayList<>();
        specs.add(spec("skill-routing", "Skill Routing", "skill_routing", "routing",
                "skill_routing_accuracy", EvaluatorSeverity.STANDARD));
        specs.add(spec("required-tool", "Required Tool", "required_tool", "tool_use",
                "tool_coverage", EvaluatorSeverity.STANDARD));
        specs.add(spec("forbidden-tool", "Forbidden Tool", "forbidden_tool", "tool_use",
                "forbidden_tool_compliance", EvaluatorSeverity.BLOCKING));
        specs.add(spec("tool-arguments", "Tool Arguments", "tool_arguments", "tool_use",
                "tool_argument_accuracy", EvaluatorSeverity.STANDARD));
        specs.add(spec("final-state", "Final State", "final_state", "state",
                "final_state_match", EvaluatorSeverity.STANDARD));
        specs.add(spec("final-output", "Final Output", "final_output", "answer",
                "final_output_match", EvaluatorSeverity.STANDARD));
        specs.add(spec("policy-compliance", "Policy Compliance", "policy_compliance", "safety",
                "policy_compliance", EvaluatorSeverity.BLOCKING));
        return Collections.unmodifiableList(specs);
    }

    private static EvaluatorSpec spec(String id, String name, String implementationId,
            String dimension, String metric, EvaluatorSeverity severity) {
        return EvaluatorSpec.of(id, name, "1", EvaluatorKind.RULE, dimension, metric,
                severity, implementationId, "1", null, null, null, "", "", "", "");
    }

    private static Map<String, Evaluator> buildBuiltinEvaluators() {
        Map<String, Evaluator> evaluators = new LinkedHashMap<>();
        for (EvaluatorSpec spec : BUILTIN_SPECS) {
            evaluators.put(spec.id(), Evaluator.of(spec.id(), spec.name(), "",
                    EvaluatorSource.BUILTIN, true, BUILTIN_DEFINED_AT, BUILTIN_DEFINED_AT,
                    "", "", ""));
        }
        return Collections.unmodifiableMap(evaluators);
    }

    private static Map<String, EvaluatorKind> buildImplementations() {
        Map<String, EvaluatorKind> implementations = new LinkedHashMap<>();
        implementations.put("skill_routing@1", EvaluatorKind.RULE);
        implementations.put("required_tool@1", EvaluatorKind.RULE);
        implementations.put("forbidden_tool@1", EvaluatorKind.RULE);
        implementations.put("tool_arguments@1", EvaluatorKind.RULE);
        implementations.put("final_state@1", EvaluatorKind.RULE);
        implementations.put("final_output@1", EvaluatorKind.RULE);
        implementations.put("policy_compliance@1", EvaluatorKind.RULE);
        implementations.put("composite@1", EvaluatorKind.HYBRID);
        return Collections.unmodifiableMap(implementations);
    }
}
