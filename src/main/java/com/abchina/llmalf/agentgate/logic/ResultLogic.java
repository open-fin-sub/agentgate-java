package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.dao.ResultDAO;
import com.abchina.llmalf.agentgate.dao.RunDAO;
import com.abchina.llmalf.agentgate.dao.TraceDAO;
import com.abchina.llmalf.agentgate.dao.entity.ResultEntity;
import com.abchina.llmalf.agentgate.dao.entity.RunEntity;
import com.abchina.llmalf.agentgate.dao.entity.TraceEntity;
import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.IdentityDigest;
import com.abchina.llmalf.agentgate.domain.model.result.EvaluationResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Result 域存储编排.
 *
 * <p>对齐 Python storage/mysql.py 的 save_results/list_results:
 * 批内唯一、Run 与 Trace 存在性、结果不可变。</p>
 */
@Component
public class ResultLogic {

    private final ResultDAO resultDAO;
    private final RunDAO runDAO;
    private final TraceDAO traceDAO;

    public ResultLogic(ResultDAO resultDAO, RunDAO runDAO, TraceDAO traceDAO) {
        this.resultDAO = resultDAO;
        this.runDAO = runDAO;
        this.traceDAO = traceDAO;
    }

    /**
     * 批量保存评测结论(批内唯一 + 归属校验 + 不可变).
     *
     * @param results 结论列表
     */
    @Transactional
    public void saveResults(List<EvaluationResult> results) {
        Set<String> ids = new HashSet<>();
        for (EvaluationResult item : results) {
            if (!ids.add(item.id())) {
                throw new IllegalArgumentException(
                        "EvaluationResult ids must be unique within a batch");
            }
        }
        Set<String> keys = new HashSet<>();
        for (EvaluationResult item : results) {
            if (!keys.add(item.caseId() + "\n" + item.evaluatorId())) {
                throw new IllegalArgumentException(
                        "EvaluationResults must be unique by Run, Case, and Evaluator");
            }
        }
        try {
            for (EvaluationResult item : results) {
                RunEntity runEntity = runDAO.selectByIdForUpdate(
                        IdentityDigest.of(item.runId()));
                if (runEntity == null) {
                    throw new IllegalArgumentException("unknown EvaluationRun");
                }
                TraceEntity traceEntity = traceDAO.selectByIdForUpdate(
                        IdentityDigest.of(item.traceId()));
                if (traceEntity == null) {
                    throw new IllegalArgumentException(
                            "EvaluationResult requires a matching Run and Trace");
                }
                ResultEntity storedEntity = resultDAO.selectByIdForUpdate(
                        IdentityDigest.of(item.id()));
                if (storedEntity != null) {
                    EvaluationResult stored = toResult(storedEntity);
                    if (!StorageModels.samePayload(stored.toPayload(), item.toPayload())) {
                        throw new IllegalArgumentException("EvaluationResult is immutable");
                    }
                } else {
                    resultDAO.insert(toEntity(item));
                }
            }
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 列出 Run 的全部结论(case_id, evaluator_id, id 升序).
     *
     * @param runId Run id
     * @return 结论列表
     */
    @Transactional
    public List<EvaluationResult> listResults(String runId) {
        List<EvaluationResult> items = new ArrayList<>();
        for (ResultEntity entity : resultDAO.selectByRunKey(IdentityDigest.of(runId))) {
            if (!entity.getRunId().equals(runId)) {
                throw new IllegalArgumentException("database identity digest collision");
            }
            items.add(toResult(entity));
        }
        items.sort(Comparator.comparing(EvaluationResult::caseId)
                .thenComparing(EvaluationResult::evaluatorId)
                .thenComparing(EvaluationResult::id));
        return items;
    }

    private static boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private EvaluationResult toResult(ResultEntity entity) {
        EvaluationResult result = EvaluationResult.fromPayload(
                StorageModels.parsePayload(entity.getPayload()));
        verifyIndexed(toEntity(result), entity);
        return result;
    }

    private ResultEntity toEntity(EvaluationResult result) {
        ResultEntity entity = new ResultEntity();
        entity.setIdKey(IdentityDigest.of(result.id()));
        entity.setId(result.id());
        entity.setRunKey(IdentityDigest.of(result.runId()));
        entity.setRunId(result.runId());
        entity.setCaseKey(IdentityDigest.of(result.caseId()));
        entity.setCaseId(result.caseId());
        entity.setTraceKey(IdentityDigest.of(result.traceId()));
        entity.setTraceId(result.traceId());
        entity.setEvaluatorKey(IdentityDigest.of(result.evaluatorId()));
        entity.setEvaluatorId(result.evaluatorId());
        entity.setPayload(CanonicalJson.serialize(result.toPayload()));
        return entity;
    }

    private static void verifyIndexed(ResultEntity rebuilt, ResultEntity stored) {
        if (!Arrays.equals(rebuilt.getIdKey(), stored.getIdKey())
                || !equalsNullable(rebuilt.getId(), stored.getId())
                || !Arrays.equals(rebuilt.getRunKey(), stored.getRunKey())
                || !equalsNullable(rebuilt.getRunId(), stored.getRunId())
                || !Arrays.equals(rebuilt.getCaseKey(), stored.getCaseKey())
                || !equalsNullable(rebuilt.getCaseId(), stored.getCaseId())
                || !Arrays.equals(rebuilt.getTraceKey(), stored.getTraceKey())
                || !equalsNullable(rebuilt.getTraceId(), stored.getTraceId())
                || !Arrays.equals(rebuilt.getEvaluatorKey(), stored.getEvaluatorKey())
                || !equalsNullable(rebuilt.getEvaluatorId(), stored.getEvaluatorId())) {
            throw new IllegalArgumentException("stored indexed columns do not match payload");
        }
    }
}
