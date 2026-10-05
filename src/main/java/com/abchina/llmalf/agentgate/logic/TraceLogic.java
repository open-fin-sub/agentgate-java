package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.dao.TraceDAO;
import com.abchina.llmalf.agentgate.dao.entity.TraceEntity;
import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.IdentityDigest;
import com.abchina.llmalf.agentgate.domain.model.trace.Trace;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Trace 域存储编排.
 *
 * <p>对齐 Python storage/mysql.py 的 Trace 系列:Run/Case 身份不可变、
 * payload 一致性守护;traces 表的 id 列即 trace_id。</p>
 */
@Component
public class TraceLogic {

    private final TraceDAO traceDAO;

    public TraceLogic(TraceDAO traceDAO) {
        this.traceDAO = traceDAO;
    }

    /**
     * 保存轨迹(存在时 Run/Case 身份不可变).
     *
     * @param trace 轨迹
     */
    @Transactional
    public void saveTrace(Trace trace) {
        try {
            TraceEntity storedEntity = traceDAO.selectByIdForUpdate(
                    IdentityDigest.of(trace.traceId()));
            Trace stored = storedEntity == null ? null : toTrace(storedEntity);
            if (stored != null) {
                if (!stored.runId().equals(trace.runId())
                        || !stored.caseId().equals(trace.caseId())) {
                    throw new IllegalArgumentException("Trace Run and Case identity are immutable");
                }
                traceDAO.updateById(toEntity(trace));
            } else {
                traceDAO.insert(toEntity(trace));
            }
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 按 Run+用例查询轨迹.
     *
     * @param runId Run id
     * @param caseId 用例 id
     * @return 轨迹(可空)
     */
    @Transactional
    public Trace getTrace(String runId, String caseId) {
        TraceEntity entity = traceDAO.selectByRunKeyAndCaseKey(IdentityDigest.of(runId),
                IdentityDigest.of(caseId));
        if (entity == null) {
            return null;
        }
        if (!entity.getRunId().equals(runId) || !entity.getCaseId().equals(caseId)) {
            throw new IllegalArgumentException("database identity digest collision");
        }
        return toTrace(entity);
    }

    /**
     * 列出 Run 的全部轨迹(case_id, trace_id 升序).
     *
     * @param runId Run id
     * @return 轨迹列表
     */
    @Transactional
    public List<Trace> listTraces(String runId) {
        List<Trace> items = new ArrayList<>();
        for (TraceEntity entity : traceDAO.selectByRunKey(IdentityDigest.of(runId))) {
            if (!entity.getRunId().equals(runId)) {
                throw new IllegalArgumentException("database identity digest collision");
            }
            items.add(toTrace(entity));
        }
        items.sort(Comparator.comparing(Trace::caseId).thenComparing(Trace::traceId));
        return items;
    }

    private static boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private Trace toTrace(TraceEntity entity) {
        Trace trace = Trace.fromPayload(StorageModels.parsePayload(entity.getPayload()));
        verifyIndexed(toEntity(trace), entity);
        return trace;
    }

    private TraceEntity toEntity(Trace trace) {
        TraceEntity entity = new TraceEntity();
        entity.setIdKey(IdentityDigest.of(trace.traceId()));
        entity.setId(trace.traceId());
        entity.setRunKey(IdentityDigest.of(trace.runId()));
        entity.setRunId(trace.runId());
        entity.setCaseKey(IdentityDigest.of(trace.caseId()));
        entity.setCaseId(trace.caseId());
        entity.setPayload(CanonicalJson.serialize(trace.toPayload()));
        return entity;
    }

    private static void verifyIndexed(TraceEntity rebuilt, TraceEntity stored) {
        if (!Arrays.equals(rebuilt.getIdKey(), stored.getIdKey())
                || !equalsNullable(rebuilt.getId(), stored.getId())
                || !Arrays.equals(rebuilt.getRunKey(), stored.getRunKey())
                || !equalsNullable(rebuilt.getRunId(), stored.getRunId())
                || !Arrays.equals(rebuilt.getCaseKey(), stored.getCaseKey())
                || !equalsNullable(rebuilt.getCaseId(), stored.getCaseId())) {
            throw new IllegalArgumentException("stored indexed columns do not match payload");
        }
    }
}
