package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.dao.RunAssetRefDAO;
import com.abchina.llmalf.agentgate.dao.RunDAO;
import com.abchina.llmalf.agentgate.dao.entity.RunAssetRefEntity;
import com.abchina.llmalf.agentgate.domain.IdentityDigest;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunLifecycle;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.service.impl.RunLaunchServiceTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MySQL 行锁原子 claim 并发测试(两个独立事务模拟两个服务实例).
 */
@SpringBootTest
class RunClaimConcurrencyIntegrationTest {

    private static final OffsetDateTime CLAIMED_AT =
            OffsetDateTime.parse("2026-01-01T01:00:00Z");
    private static final OffsetDateTime ANCIENT_CREATED =
            OffsetDateTime.parse("1899-12-31T00:00:00Z");
    private static final OffsetDateTime ANCIENT_DUE =
            OffsetDateTime.parse("1900-01-01T00:00:00Z");

    @Autowired private RunLogic runLogic;
    @Autowired private RunDAO runDAO;
    @Autowired private RunAssetRefDAO runAssetRefDAO;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<String> createdRunIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        TransactionTemplate transaction = newTransaction();
        transaction.execute(status -> {
            for (String runId : createdRunIds) {
                byte[] runKey = IdentityDigest.of(runId);
                for (RunAssetRefEntity ref : runAssetRefDAO.selectByRunKey(runKey)) {
                    runAssetRefDAO.deleteByKey(ref.getReferenceKey());
                }
                runDAO.deleteByKey(runKey);
            }
            return null;
        });
        createdRunIds.clear();
    }

    @Test
    void pendingRunIsClaimedByOnlyOneInstance() throws Exception {
        EvaluationRun pending = RunLaunchServiceTest.run(uniqueId("pending"),
                RunStatus.PENDING, "v1");
        save(pending);

        List<EvaluationRun> claims = invokeConcurrently(
                () -> runLogic.claimPendingRun(pending.id(), CLAIMED_AT));

        assertEquals(1, nonNullCount(claims));
        assertEquals(RunStatus.RUNNING,
                runLogic.getRun(pending.id(), RunLaunchServiceTest.TEAM).status());
    }

    @Test
    void waitingRunIsClaimedByOnlyOneInstance() throws Exception {
        EvaluationRun pending = RunLaunchServiceTest.run(uniqueId("waiting"),
                RunStatus.PENDING, "v1");
        EvaluationRun waiting = pending.transition(RunStatus.WAITING, CLAIMED_AT, null);
        save(waiting);

        List<EvaluationRun> claims = invokeConcurrently(
                () -> runLogic.claimWaitingRun(waiting.id(), CLAIMED_AT.plusMinutes(1)));

        assertEquals(1, nonNullCount(claims));
        assertEquals(RunStatus.PENDING,
                runLogic.getRun(waiting.id(), RunLaunchServiceTest.TEAM).status());
    }

    @Test
    void dueScheduledRunIsReleasedByOnlyOneInstance() throws Exception {
        String id = uniqueId("scheduled");
        EvaluationRun fixture = RunLaunchServiceTest.run(id, RunStatus.PENDING, "v1");
        EvaluationRun scheduled = EvaluationRun.of(id, fixture.manifest(),
                RunLifecycle.of(RunStatus.SCHEDULED, ANCIENT_CREATED, ANCIENT_DUE,
                        null, null, null),
                fixture.userTeamId(), fixture.userId(), fixture.userName(),
                fixture.apiKey(), 0);
        save(scheduled);

        List<List<EvaluationRun>> batches = invokeConcurrently(
                () -> runLogic.claimDueScheduledRuns(ANCIENT_DUE.plusSeconds(1), 1));
        int occurrences = 0;
        for (List<EvaluationRun> batch : batches) {
            for (EvaluationRun claimed : batch) {
                if (id.equals(claimed.id())) {
                    occurrences++;
                }
            }
        }

        assertEquals(1, occurrences);
        assertEquals(RunStatus.PENDING,
                runLogic.getRun(id, RunLaunchServiceTest.TEAM).status());
    }

    private void save(EvaluationRun run) {
        createdRunIds.add(run.id());
        newTransaction().execute(status -> {
            runLogic.saveRun(run);
            return null;
        });
        assertNotNull(runLogic.getRun(run.id(), RunLaunchServiceTest.TEAM));
    }

    private <T> List<T> invokeConcurrently(Callable<T> operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<T> transactional = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("claim workers did not start together");
                }
                return newTransaction().execute(status -> {
                    try {
                        return operation.call();
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
            };
            Future<T> first = executor.submit(transactional);
            Future<T> second = executor.submit(transactional);
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("claim workers were not ready");
            }
            start.countDown();
            return java.util.Arrays.asList(first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(10);
        return transaction;
    }

    private static int nonNullCount(List<?> values) {
        int count = 0;
        for (Object value : values) {
            if (value != null) {
                count++;
            }
        }
        return count;
    }

    private static String uniqueId(String kind) {
        return "it-concurrent-" + kind + "-" + UUID.randomUUID();
    }
}
