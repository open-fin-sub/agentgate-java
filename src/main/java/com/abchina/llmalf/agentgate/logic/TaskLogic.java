package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.dao.EvaluationTaskDAO;
import com.abchina.llmalf.agentgate.dao.EvaluationTaskRunDAO;
import com.abchina.llmalf.agentgate.dao.RunDAO;
import com.abchina.llmalf.agentgate.dao.SkillAnalysisReportDAO;
import com.abchina.llmalf.agentgate.dao.entity.EvaluationTaskEntity;
import com.abchina.llmalf.agentgate.dao.entity.EvaluationTaskRunEntity;
import com.abchina.llmalf.agentgate.dao.entity.RunEntity;
import com.abchina.llmalf.agentgate.dao.entity.SkillAnalysisReportEntity;
import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.IdentityDigest;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTaskKind;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 评测任务域存储编排.
 *
 * <p>对齐 Python storage/mysql.py 的 evaluation_tasks 系列:
 * 任务-Run 关联校验、不可变字段、静态报告存在性与去重、
 * 新建时写入关联表。</p>
 */
@Component
public class TaskLogic {

    private final EvaluationTaskDAO evaluationTaskDAO;
    private final EvaluationTaskRunDAO evaluationTaskRunDAO;
    private final RunDAO runDAO;
    private final SkillAnalysisReportDAO skillAnalysisReportDAO;
    private final RunLogic runLogic;

    public TaskLogic(EvaluationTaskDAO evaluationTaskDAO,
            EvaluationTaskRunDAO evaluationTaskRunDAO, RunDAO runDAO,
            SkillAnalysisReportDAO skillAnalysisReportDAO, RunLogic runLogic) {
        this.evaluationTaskDAO = evaluationTaskDAO;
        this.evaluationTaskRunDAO = evaluationTaskRunDAO;
        this.runDAO = runDAO;
        this.skillAnalysisReportDAO = skillAnalysisReportDAO;
        this.runLogic = runLogic;
    }

    /**
     * 保存任务与其 Run(新 Run 连带资产引用,任务走统一保存).
     *
     * @param task 任务
     * @param runs 关联的 Run 列表(顺序与 run_ids 一致)
     */
    @Transactional
    public void saveTaskRuns(EvaluationTask task, List<EvaluationRun> runs) {
        if (task.runIds().size() != runs.size()) {
            throw new IllegalArgumentException("invalid task run associations");
        }
        for (int i = 0; i < runs.size(); i++) {
            if (!task.runIds().get(i).equals(runs.get(i).id())) {
                throw new IllegalArgumentException("invalid task run associations");
            }
        }
        for (EvaluationRun run : runs) {
            if (run.status() != RunStatus.PENDING && run.status() != RunStatus.SCHEDULED) {
                throw new IllegalArgumentException("task creation requires unstarted runs");
            }
        }
        if (task.kind() == EvaluationTaskKind.STABILITY) {
            for (EvaluationRun run : runs) {
                if (!StorageModels.samePayload(runs.get(0).manifest().toPayload(),
                        run.manifest().toPayload())
                        || run.status() != RunStatus.PENDING) {
                    throw new IllegalArgumentException(
                            "stability requires identical snapshots and pending runs");
                }
            }
        }
        try {
            for (EvaluationRun run : runs) {
                runLogic.insertRun(run);
            }
            saveTask(task);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 保存任务(run 存在性 + 不可变字段 + 静态报告校验).
     *
     * @param task 任务
     * @return 保存后的任务(静态报告去重合并后)
     */
    @Transactional
    public EvaluationTask saveTask(EvaluationTask task) {
        try {
            List<String> sortedRunIds = new ArrayList<>(task.runIds());
            java.util.Collections.sort(sortedRunIds);
            for (String runId : sortedRunIds) {
                RunEntity runEntity = runDAO.selectByIdForUpdate(IdentityDigest.of(runId));
                if (runEntity == null) {
                    throw new IllegalArgumentException("unknown EvaluationRun");
                }
            }
            EvaluationTaskEntity previousEntity = evaluationTaskDAO.selectByIdForUpdate(
                    IdentityDigest.of(task.id()));
            EvaluationTask previous = previousEntity == null ? null : toTask(previousEntity);
            if (previous != null) {
                if (previous.kind() != task.kind()
                        || !previous.runIds().equals(task.runIds())
                        || !previous.gitCommitRefs().equals(task.gitCommitRefs())
                        || !equalsNullable(previous.credentialId(), task.credentialId())) {
                    throw new IllegalArgumentException(
                            "task identity and run associations are immutable");
                }
            }
            java.util.Map<String, String> reports = new java.util.LinkedHashMap<>();
            List<String> reportIds = new ArrayList<>();
            if (previous != null) {
                reportIds.addAll(previous.staticReportIds());
            }
            reportIds.addAll(task.staticReportIds());
            for (String reportId : reportIds) {
                SkillAnalysisReportEntity reportEntity = skillAnalysisReportDAO.selectByKey(
                        IdentityDigest.of(reportId));
                if (reportEntity == null) {
                    throw new IllegalArgumentException("unknown static report");
                }
                reports.put(reportEntity.getTargetDescriptorSha256(), reportId);
            }
            EvaluationTask effective = task;
            if (previous != null) {
                effective = EvaluationTask.of(previous.id(), previous.kind(),
                        previous.createdAt(), previous.runIds(),
                        new ArrayList<>(reports.values()), previous.gitCommitRefs(),
                        previous.credentialId());
                evaluationTaskDAO.updateById(toEntity(effective));
            } else {
                effective = EvaluationTask.of(task.id(), task.kind(), task.createdAt(),
                        task.runIds(), new ArrayList<>(reports.values()),
                        task.gitCommitRefs(), task.credentialId());
                evaluationTaskDAO.insert(toEntity(effective));
                for (String runId : task.runIds()) {
                    EvaluationTaskRunEntity link = new EvaluationTaskRunEntity();
                    link.setRunKey(IdentityDigest.of(runId));
                    link.setRunId(runId);
                    link.setTaskKey(IdentityDigest.of(task.id()));
                    link.setTaskId(task.id());
                    evaluationTaskRunDAO.insert(link);
                }
            }
            return effective;
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 查询任务.
     *
     * @param taskId 任务 id
     * @return 任务(可空)
     */
    @Transactional
    public EvaluationTask getEvaluationTask(String taskId) {
        EvaluationTaskEntity entity = evaluationTaskDAO.selectByKey(IdentityDigest.of(taskId));
        if (entity == null) {
            return null;
        }
        if (!entity.getId().equals(taskId)) {
            throw new IllegalArgumentException("database identity digest collision");
        }
        return toTask(entity);
    }

    /**
     * 列出全部任务(created_at 降序).
     *
     * @return 任务列表
     */
    @Transactional
    public List<EvaluationTask> listEvaluationTasks() {
        List<EvaluationTask> items = new ArrayList<>();
        for (EvaluationTaskEntity entity : evaluationTaskDAO.selectAll()) {
            items.add(toTask(entity));
        }
        return StorageOrdering.ordered(items, EvaluationTask::createdAt, EvaluationTask::id);
    }

    private static boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private EvaluationTask toTask(EvaluationTaskEntity entity) {
        EvaluationTask task = EvaluationTask.fromPayload(
                StorageModels.parsePayload(entity.getPayload()));
        verifyIndexed(toEntity(task), entity);
        return task;
    }

    private EvaluationTaskEntity toEntity(EvaluationTask task) {
        EvaluationTaskEntity entity = new EvaluationTaskEntity();
        entity.setIdKey(IdentityDigest.of(task.id()));
        entity.setId(task.id());
        entity.setCreatedAt(task.createdAt().toLocalDateTime());
        entity.setPayload(CanonicalJson.serialize(task.toPayload()));
        return entity;
    }

    private static void verifyIndexed(EvaluationTaskEntity rebuilt, EvaluationTaskEntity stored) {
        if (!Arrays.equals(rebuilt.getIdKey(), stored.getIdKey())
                || !equalsNullable(rebuilt.getId(), stored.getId())
                || !equalsNullable(rebuilt.getCreatedAt(), stored.getCreatedAt())) {
            throw new IllegalArgumentException("stored indexed columns do not match payload");
        }
    }
}
