package com.abchina.llmalf.agentgate.dao;

import com.abchina.llmalf.agentgate.dao.entity.DatasetEntity;
import com.abchina.llmalf.agentgate.dao.entity.EvaluationTaskRunEntity;
import com.abchina.llmalf.agentgate.dao.entity.EvaluatorVersionEntity;
import com.abchina.llmalf.agentgate.dao.entity.RunEntity;
import com.abchina.llmalf.agentgate.domain.IdentityDigest;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DAO 层共库集成测试.
 *
 * <p>读侧:对现有数据(Python 写入)断言不变量——id_key 与 IdentityDigest
 * 逐字节一致、payload 可被 domain fromPayload 解析、status 为合法 wire 值;
 * 写侧:随机 UUID 测试数据 + Spring 测试事务回滚,不污染共库。</p>
 */
@SpringBootTest
@Transactional
class DaoIntegrationTest {

    @Autowired
    private RunDAO runDAO;

    @Autowired
    private DatasetDAO datasetDAO;

    @Autowired
    private EvaluatorVersionDAO evaluatorVersionDAO;

    @Autowired
    private EvaluationTaskRunDAO evaluationTaskRunDAO;

    @Autowired
    private ApiKeyDAO apiKeyDAO;

    @Autowired
    private EvaluationTaskDAO evaluationTaskDAO;

    @Test
    void storedRunsSatisfyIdentityDigestInvariant() {
        List<RunEntity> rows = runDAO.selectList(
                new QueryWrapper<RunEntity>().last("LIMIT 5"));
        for (RunEntity row : rows) {
            assertArrayEquals(IdentityDigest.of(row.getId()), row.getIdKey(),
                    "id_key must equal IdentityDigest.of(id): " + row.getId());
            assertTrue(Arrays.asList("scheduled", "pending", "waiting", "running",
                    "completed", "failed", "cancelled").contains(row.getStatus()),
                    "status must be a legal wire value: " + row.getStatus());
            EvaluationRun run = EvaluationRun.fromPayload(payloadOf(row.getPayload()));
            assertEquals(row.getId(), run.id());
            assertEquals(row.getStatus(), run.status().wireValue());
        }
    }

    @Test
    void storedDatasetsSatisfyIdentityDigestInvariant() {
        List<DatasetEntity> rows = datasetDAO.selectList(
                new QueryWrapper<DatasetEntity>().last("LIMIT 5"));
        for (DatasetEntity row : rows) {
            assertArrayEquals(IdentityDigest.of(row.getId()), row.getIdKey(),
                    "id_key must equal IdentityDigest.of(id): " + row.getId());
            Map<String, Object> payload = payloadOf(row.getPayload());
            assertEquals(row.getId(), payload.get("id"));
        }
    }

    @Test
    void runCrudRoundTripsWithinTestTransaction() {
        String id = "it-run-" + UUID.randomUUID();
        RunEntity entity = new RunEntity();
        entity.setIdKey(IdentityDigest.of(id));
        entity.setId(id);
        entity.setStatus("pending");
        entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        entity.setScheduledFor(null);
        entity.setUserTeamKey(IdentityDigest.of("it-team"));
        entity.setUserTeamId("it-team");
        entity.setUserId("it-user");
        entity.setUserName("集成测试");
        entity.setApiKey("sk-it");
        entity.setPayload("{\"id\":\"" + id + "\"}");
        runDAO.insert(entity);

        RunEntity reloaded = runDAO.selectByKey(IdentityDigest.of(id));
        assertNotNull(reloaded);
        assertEquals(id, reloaded.getId());
        assertEquals("pending", reloaded.getStatus());
        assertEquals("sk-it", reloaded.getApiKey());
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0, 0), reloaded.getCreatedAt());
        assertNull(reloaded.getScheduledFor());
        assertArrayEquals(IdentityDigest.of("it-team"), reloaded.getUserTeamKey());

        RunEntity locked = runDAO.selectByIdForUpdate(IdentityDigest.of(id));
        assertNotNull(locked, "FOR UPDATE lock read must work inside the test transaction");
        assertEquals(id, locked.getId());

        reloaded.setStatus("running");
        runDAO.updateById(reloaded);
        assertEquals("running", runDAO.selectByKey(IdentityDigest.of(id)).getStatus());

        List<RunEntity> byTeam = runDAO.selectByTeamKey(IdentityDigest.of("it-team"));
        assertTrue(byTeam.stream().anyMatch(row -> id.equals(row.getId())));

        List<RunEntity> byStatus = runDAO.selectByStatus("running", null);
        assertTrue(byStatus.stream().anyMatch(row -> id.equals(row.getId())));
    }

    @Test
    void evaluatorVersionCompositeKeyOperationsWork() {
        String evaluatorId = "it-eval-" + UUID.randomUUID();
        byte[] evaluatorKey = IdentityDigest.of(evaluatorId);
        EvaluatorVersionEntity entity = new EvaluatorVersionEntity();
        entity.setEvaluatorKey(evaluatorKey);
        entity.setVersion(1L);
        entity.setEvaluatorId(evaluatorId);
        entity.setContentSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        entity.setUserTeamKey(IdentityDigest.of("it-team"));
        entity.setUserTeamId("it-team");
        entity.setUserId("it-user");
        entity.setUserName("集成测试");
        entity.setPayload("{\"id\":\"" + evaluatorId + "\"}");
        assertEquals(1, evaluatorVersionDAO.insert(entity));

        EvaluatorVersionEntity byKeys = evaluatorVersionDAO
                .selectByEvaluatorKeyAndVersion(evaluatorKey, 1L);
        assertNotNull(byKeys);
        assertEquals(evaluatorId, byKeys.getEvaluatorId());
        assertEquals(Long.valueOf(1L), byKeys.getVersion());

        List<EvaluatorVersionEntity> byEvaluator = evaluatorVersionDAO
                .selectByEvaluatorKey(evaluatorKey);
        assertEquals(1, byEvaluator.size());
    }

    @Test
    void taskRunCompositeKeyOperationsWork() {
        String taskId = "it-task-" + UUID.randomUUID();
        String runId = "it-run-" + UUID.randomUUID();
        EvaluationTaskRunEntity entity = new EvaluationTaskRunEntity();
        entity.setRunKey(IdentityDigest.of(runId));
        entity.setRunId(runId);
        entity.setTaskKey(IdentityDigest.of(taskId));
        entity.setTaskId(taskId);
        assertEquals(1, evaluationTaskRunDAO.insert(entity));

        EvaluationTaskRunEntity byRun = evaluationTaskRunDAO
                .selectByRunKey(IdentityDigest.of(runId));
        assertNotNull(byRun);
        assertEquals(taskId, byRun.getTaskId());

        List<EvaluationTaskRunEntity> byTask = evaluationTaskRunDAO
                .selectByTaskKey(IdentityDigest.of(taskId));
        assertEquals(1, byTask.size());

        assertEquals(1, evaluationTaskRunDAO.deleteByRunKey(IdentityDigest.of(runId)));
        assertNull(evaluationTaskRunDAO.selectByRunKey(IdentityDigest.of(runId)));
    }

    @Test
    void fullScanQueriesExecute() {
        assertNotNull(apiKeyDAO.selectAll());
        assertNotNull(evaluationTaskDAO.selectAll());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(String payload) {
        Map<String, Object> tree = (Map<String, Object>) parseTree(payload);
        return tree;
    }

    private static Object parseTree(String payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(payload,
                    Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("stored payload is not valid JSON", e);
        }
    }
}
