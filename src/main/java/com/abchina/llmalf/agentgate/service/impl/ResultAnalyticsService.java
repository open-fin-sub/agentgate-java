package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.UserContextHolder;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.cases.CaseTurn;
import com.abchina.llmalf.agentgate.domain.model.expectation.Expectation;
import com.abchina.llmalf.agentgate.domain.model.expectation.SkillRouteExpectation;
import com.abchina.llmalf.agentgate.domain.model.expectation.ToolCallExpectation;
import com.abchina.llmalf.agentgate.domain.model.result.CheckResult;
import com.abchina.llmalf.agentgate.domain.model.result.EvaluationResult;
import com.abchina.llmalf.agentgate.domain.model.result.Outcome;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.logic.ResultLogic;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import org.springframework.stereotype.Service;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 结果分析服务.
 *
 * <p>对齐 Python result/analytics.py::calculate_result_analytics:
 * 7 个维度(evaluator/category/difficulty/tag/routing/tool_use/failure_type)
 * 的 outcome/score 分布分桶。</p>
 */
@Service
public class ResultAnalyticsService {

    private final RunLogic runLogic;
    private final ResultLogic resultLogic;

    public ResultAnalyticsService(RunLogic runLogic, ResultLogic resultLogic) {
        this.runLogic = runLogic;
        this.resultLogic = resultLogic;
    }

    /**
     * 计算已完成 Run 的分析.
     *
     * @param runId Run id
     * @return 分析投影(7 个 breakdown)
     */
    public Map<String, Object> analytics(String runId) {
        EvaluationRun run = getRun(runId);
        if (run.status() != RunStatus.COMPLETED) {
            throw new AgentException(409,
                    "Result analytics requires a completed EvaluationRun");
        }
        List<EvaluationResult> results = resultLogic.listResults(run.id());
        validateInputs(run, results);
        Map<String, Case> cases = new HashMap<>();
        for (Case item : run.manifest().executionCases()) {
            cases.put(item.id(), item);
        }

        Groups evaluatorGroups = new Groups();
        Groups categoryGroups = new Groups();
        Groups difficultyGroups = new Groups();
        Groups tagGroups = new Groups();
        Groups failureGroups = new Groups();
        for (EvaluationResult result : results) {
            Case caseItem = cases.get(result.caseId());
            add(evaluatorGroups, result.evaluatorId(), result.evaluatorName(), result.caseId(),
                    result.outcome(), result.score());
            add(categoryGroups, caseItem.category().wireValue(), caseItem.category().wireValue(),
                    result.caseId(), result.outcome(), result.score());
            add(difficultyGroups, caseItem.difficulty().wireValue(),
                    caseItem.difficulty().wireValue(), result.caseId(), result.outcome(),
                    result.score());
            for (String tag : caseItem.tags()) {
                add(tagGroups, tag, tag, result.caseId(), result.outcome(), result.score());
            }
            if (result.outcome() == Outcome.FAIL) {
                if (result.primaryFailureStage() == null) {
                    throw new AgentException(409, "failed Result has no primary failure stage");
                }
                String key = result.primaryFailureStage().wireValue();
                add(failureGroups, key, key, result.caseId(), result.outcome(), result.score());
            } else if (result.outcome() == Outcome.ERROR) {
                if (result.errorDetail() == null) {
                    throw new AgentException(409, "error Result has no error detail");
                }
                String key = "evaluator_error:" + result.errorDetail().category();
                add(failureGroups, key, key, result.caseId(), result.outcome(), result.score());
            }
        }

        Groups routingGroups = new Groups();
        Groups toolGroups = new Groups();
        Map<String, Object> expectationIndex = new HashMap<>();
        for (Case caseItem : run.manifest().executionCases()) {
            for (CaseTurn turn
                    : caseItem.turns()) {
                for (Expectation expectation
                        : turn.expectations()) {
                    if (expectation instanceof SkillRouteExpectation
                            || expectation instanceof ToolCallExpectation) {
                        expectationIndex.put(caseItem.id() + "\n" + turn.id() + "\n"
                                + expectation.id(), expectation);
                    }
                }
            }
        }
        for (EvaluationResult result : results) {
            for (CheckResult check : result.checks()) {
                if (check.turnId() == null || check.expectationId() == null) {
                    continue;
                }
                Object expectation = expectationIndex.get(result.caseId() + "\n"
                        + check.turnId() + "\n" + check.expectationId());
                if (expectation instanceof SkillRouteExpectation) {
                    String key;
                    if (check.actualMissing()) {
                        key = "missing";
                    } else if (check.actual() instanceof String
                            && !((String) check.actual()).trim().isEmpty()) {
                        key = (String) check.actual();
                    } else {
                        key = "ambiguous";
                    }
                    add(routingGroups, key, key, result.caseId(), check.outcome(), check.score());
                } else if (expectation instanceof ToolCallExpectation) {
                    String tool = ((ToolCallExpectation) expectation).tool();
                    add(toolGroups, tool, tool, result.caseId(), check.outcome(), check.score());
                }
            }
        }

        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("run_id", run.id());
        analytics.put("by_evaluator", breakdown("evaluator", evaluatorGroups));
        analytics.put("by_category", breakdown("category", categoryGroups));
        analytics.put("by_difficulty", breakdown("difficulty", difficultyGroups));
        analytics.put("by_tag", breakdown("tag", tagGroups));
        analytics.put("by_routing", breakdown("routing", routingGroups));
        analytics.put("by_tool_use", breakdown("tool_use", toolGroups));
        analytics.put("by_failure_type", breakdown("failure_type", failureGroups));
        return analytics;
    }

    private static void validateInputs(EvaluationRun run, List<EvaluationResult> results) {
        Set<String> ids = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (EvaluationResult result : results) {
            if (!ids.add(result.id())) {
                throw new AgentException(409,
                        "Result analytics requires unique EvaluationResult ids");
            }
            if (!keys.add(result.caseId() + "\n" + result.evaluatorId())) {
                throw new AgentException(409,
                        "Result analytics requires unique Case and Evaluator pairs");
            }
            if (!result.runId().equals(run.id())) {
                throw new AgentException(409,
                        "EvaluationResult belongs to a different Run");
            }
        }
        Set<String> known = new HashSet<>();
        for (Case item : run.manifest().executionCases()) {
            known.add(item.id());
        }
        Set<String> unknown = new HashSet<>();
        for (EvaluationResult result : results) {
            if (!known.contains(result.caseId())) {
                unknown.add(result.caseId());
            }
        }
        if (!unknown.isEmpty()) {
            List<String> sorted = new ArrayList<>(unknown);
            Collections.sort(sorted);
            throw new AgentException(409,
                    "EvaluationResults reference unknown Cases: " + String.join(", ", sorted));
        }
    }

    private static void add(Groups groups, String key, String label, String caseId,
            Outcome outcome, Double score) {
        groups.add(key, label, caseId, outcome, score);
    }

    private static Map<String, Object> breakdown(String dimension, Groups groups) {
        List<Map<String, Object>> buckets = new ArrayList<>();
        for (Map.Entry<String, List<Item>> entry : groups.sorted()) {
            buckets.add(bucket(entry.getKey(), entry.getValue()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", dimension);
        result.put("available", !buckets.isEmpty());
        result.put("buckets", buckets);
        return result;
    }

    private static Map<String, Object> bucket(String key, List<Item> items) {
        int passed = 0;
        int failed = 0;
        int reviewed = 0;
        int notApplicable = 0;
        int errors = 0;
        Set<String> caseIds = new HashSet<>();
        List<Double> scores = new ArrayList<>();
        String label = null;
        for (Item item : items) {
            label = item.label;
            caseIds.add(item.caseId);
            if (item.outcome == Outcome.PASS) {
                passed++;
            } else if (item.outcome == Outcome.FAIL) {
                failed++;
            } else if (item.outcome == Outcome.REVIEW) {
                reviewed++;
            } else if (item.outcome == Outcome.NOT_APPLICABLE) {
                notApplicable++;
            } else if (item.outcome == Outcome.ERROR) {
                errors++;
            }
            if (item.score != null) {
                scores.add(item.score);
            }
        }
        int applicable = passed + failed + reviewed;
        Map<String, Object> bucket = new LinkedHashMap<>();
        bucket.put("key", key);
        bucket.put("label", label);
        bucket.put("case_count", caseIds.size());
        bucket.put("observation_count", items.size());
        bucket.put("passed", passed);
        bucket.put("failed", failed);
        bucket.put("reviewed", reviewed);
        bucket.put("not_applicable", notApplicable);
        bucket.put("errors", errors);
        bucket.put("applicable", applicable);
        bucket.put("pass_rate", applicable == 0 ? null : (double) passed / applicable);
        bucket.put("failure_rate", applicable == 0 ? null : (double) failed / applicable);
        bucket.put("average_score", scores.isEmpty() ? null
                : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        return bucket;
    }

    private EvaluationRun getRun(String runId) {
        EvaluationRun run = runLogic.getRun(runId,
                UserContextHolder.current().userTeamId());
        if (run == null) {
            throw new AgentException(404, "unknown EvaluationRun: " + runId);
        }
        return run;
    }

    private static final class Item {
        private final String label;
        private final String caseId;
        private final Outcome outcome;
        private final Double score;

        private Item(String label, String caseId, Outcome outcome, Double score) {
            this.label = label;
            this.caseId = caseId;
            this.outcome = outcome;
            this.score = score;
        }
    }

    private static final class Groups {
        private final Map<String, String> labels = new LinkedHashMap<>();
        private final Map<String, List<Item>> items = new LinkedHashMap<>();

        void add(String key, String label, String caseId, Outcome outcome, Double score) {
            String existing = labels.get(key);
            if (existing != null && !existing.equals(label)) {
                throw new AgentException(409,
                        "analytics key '" + key + "' has conflicting labels");
            }
            labels.putIfAbsent(key, label);
            items.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new Item(label, caseId, outcome, score));
        }

        List<Map.Entry<String, List<Item>>> sorted() {
            List<String> keys = new ArrayList<>(items.keySet());
            Collections.sort(keys);
            List<Map.Entry<String, List<Item>>> result = new ArrayList<>(keys.size());
            for (String key : keys) {
                result.add(new SimpleEntry<>(key, items.get(key)));
            }
            return result;
        }
    }
}
