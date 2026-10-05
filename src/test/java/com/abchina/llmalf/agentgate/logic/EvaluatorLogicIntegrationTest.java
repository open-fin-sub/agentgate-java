package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.domain.model.evaluator.CombinationPolicy;
import com.abchina.llmalf.agentgate.domain.model.evaluator.Evaluator;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorDraft;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorKind;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorRef;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSeverity;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSource;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EvaluatorLogic 共库集成测试(随机 id + 事务回滚).
 *
 * <p>覆盖 save/draft/publish/delete 的正反路径,消息文本与
 * Python storage/mysql.py 逐字对照。</p>
 */
@SpringBootTest
@Transactional
class EvaluatorLogicIntegrationTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");

    @Autowired
    private EvaluatorLogic evaluatorLogic;

    private String newId() {
        return "it-ev-" + UUID.randomUUID();
    }

    private Evaluator evaluatorOf(String id, OffsetDateTime created, OffsetDateTime updated) {
        return Evaluator.of(id, "评测器-" + id, "描述", EvaluatorSource.USER, true,
                created, updated, "it-team", "u", "测试");
    }

    private EvaluatorDraft draftOf(String id, String evaluatorId, OffsetDateTime created,
            OffsetDateTime updated) {
        return EvaluatorDraft.of(id, evaluatorId, null, EvaluatorKind.RULE, "state",
                "state_metric", EvaluatorSeverity.STANDARD, "final_state", "1", null, null,
                null, created, updated, "it-team", "u", "测试");
    }

    @Test
    void saveEvaluatorGuards() {
        String id = newId();
        evaluatorLogic.saveEvaluator(evaluatorOf(id, T0, T0));
        assertEquals("评测器-" + id, evaluatorLogic.getEvaluator(id, "it-team").name());

        evaluatorLogic.saveEvaluator(evaluatorOf(id, T0, T1));
        assertEquals(T1, evaluatorLogic.getEvaluator(id, "it-team").updatedAt());

        assertEquals("Evaluator source and created_at are immutable",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic.saveEvaluator(
                        Evaluator.of(id, "n", "d", EvaluatorSource.USER, true, T1, T1,
                                "it-team", "u", "测试"))).getMessage());
        assertEquals("cannot save a stale Evaluator",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic.saveEvaluator(
                        evaluatorOf(id, T0, T0))).getMessage());
        assertNull(evaluatorLogic.getEvaluator(id, "other-team"));
    }

    @Test
    void draftLifecycleAndPublish() {
        String evaluatorId = newId();
        String draftId = newId();
        Evaluator evaluator = evaluatorOf(evaluatorId, T0, T0);
        EvaluatorDraft draft = draftOf(draftId, evaluatorId, T0, T0);

        assertEquals("unknown Evaluator",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic
                        .saveEvaluatorDraft(draft)).getMessage());

        evaluatorLogic.saveEvaluatorWithDraft(evaluator, draft);
        assertEquals(draftId, evaluatorLogic.getEvaluatorDraft(evaluatorId, "it-team").id());

        assertEquals("EvaluatorDraft must belong to Evaluator and not precede its creation",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic
                        .saveEvaluatorWithDraft(evaluator,
                                draftOf(newId(), "other-ev", T0.minusDays(1), T0)))
                        .getMessage());

        evaluatorLogic.saveEvaluatorDraft(draftOf(draftId, evaluatorId, T0, T1));
        assertEquals(T1, evaluatorLogic.getEvaluatorDraft(evaluatorId, "it-team").updatedAt());
        assertEquals("cannot save a stale EvaluatorDraft",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic
                        .saveEvaluatorDraft(draftOf(draftId, evaluatorId, T0, T0))).getMessage());
        assertEquals("EvaluatorDraft created_at is immutable",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic
                        .saveEvaluatorDraft(draftOf(draftId, evaluatorId, T1, T1))).getMessage());

        EvaluatorSpec published = EvaluatorSpec.of(evaluatorId, evaluator.name(), "1",
                draft.kind(), draft.dimension(), draft.metric(), draft.severity(),
                draft.implementationId(), draft.implementationVersion(), draft.config(),
                draft.children(), draft.combination(), "", draft.userTeamId(), draft.userId(),
                draft.userName());

        assertEquals("expected Evaluator draft does not exist",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic
                        .publishEvaluatorDraft("missing-draft", published)).getMessage());

        EvaluatorSpec wrongVersion = EvaluatorSpec.of(evaluatorId, evaluator.name(), "2",
                draft.kind(), draft.dimension(), draft.metric(), draft.severity(),
                draft.implementationId(), draft.implementationVersion(), draft.config(),
                draft.children(), draft.combination(), "", draft.userTeamId(), draft.userId(),
                draft.userName());
        assertEquals("Evaluator publication requires version 1",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic
                        .publishEvaluatorDraft(draftId, wrongVersion)).getMessage());

        EvaluatorSpec wrongName = EvaluatorSpec.of(evaluatorId, "改名", "1",
                draft.kind(), draft.dimension(), draft.metric(), draft.severity(),
                draft.implementationId(), draft.implementationVersion(), draft.config(),
                draft.children(), draft.combination(), "", draft.userTeamId(), draft.userId(),
                draft.userName());
        assertEquals("published EvaluatorSpec does not match the current draft",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic
                        .publishEvaluatorDraft(draftId, wrongName)).getMessage());

        evaluatorLogic.publishEvaluatorDraft(draftId, published);
        assertNull(evaluatorLogic.getEvaluatorDraft(evaluatorId, "it-team"));
        List<EvaluatorSpec> versions = evaluatorLogic.listEvaluatorVersions(evaluatorId,
                "it-team");
        assertEquals(1, versions.size());
        assertEquals("1", versions.get(0).version());
        assertEquals(published.contentSha256(), versions.get(0).contentSha256());
        assertEquals("published Evaluator cannot be deleted",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic
                        .deleteUnpublishedEvaluator(evaluatorId, "it-team")).getMessage());
    }

    @Test
    void secondPublicationRequiresNextVersion() {
        String evaluatorId = newId();
        String draftId = newId();
        Evaluator evaluator = evaluatorOf(evaluatorId, T0, T0);
        EvaluatorDraft draft = draftOf(draftId, evaluatorId, T0, T0);
        evaluatorLogic.saveEvaluatorWithDraft(evaluator, draft);
        EvaluatorSpec first = EvaluatorSpec.of(evaluatorId, evaluator.name(), "1",
                draft.kind(), draft.dimension(), draft.metric(), draft.severity(),
                draft.implementationId(), draft.implementationVersion(), draft.config(),
                draft.children(), draft.combination(), "", draft.userTeamId(), draft.userId(),
                draft.userName());
        evaluatorLogic.publishEvaluatorDraft(draftId, first);

        String secondDraftId = newId();
        EvaluatorDraft secondDraft = draftOf(secondDraftId, evaluatorId, T0, T1);
        evaluatorLogic.saveEvaluatorDraft(secondDraft);
        EvaluatorSpec second = EvaluatorSpec.of(evaluatorId, evaluator.name(), "2",
                secondDraft.kind(), secondDraft.dimension(), secondDraft.metric(),
                secondDraft.severity(), secondDraft.implementationId(),
                secondDraft.implementationVersion(), secondDraft.config(),
                secondDraft.children(), secondDraft.combination(), "",
                secondDraft.userTeamId(), secondDraft.userId(), secondDraft.userName());
        evaluatorLogic.publishEvaluatorDraft(secondDraftId, second);

        List<EvaluatorSpec> versions = evaluatorLogic.listEvaluatorVersions(evaluatorId,
                "it-team");
        assertEquals(2, versions.size());
        assertEquals("2", versions.get(0).version());
        assertEquals("1", versions.get(1).version());
        assertEquals("2", evaluatorLogic.getEvaluatorVersion(evaluatorId, "2", "it-team")
                .version());
        assertEquals("2", evaluatorLogic.getLatestEvaluatorVersion(evaluatorId, "it-team")
                .version());
    }

    @Test
    void deleteUnpublishedEvaluatorRemovesDraft() {
        String evaluatorId = newId();
        String draftId = newId();
        evaluatorLogic.saveEvaluatorWithDraft(evaluatorOf(evaluatorId, T0, T0),
                draftOf(draftId, evaluatorId, T0, T0));

        assertEquals("unknown Evaluator",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic
                        .deleteUnpublishedEvaluator(evaluatorId, "other-team")).getMessage());

        evaluatorLogic.deleteUnpublishedEvaluator(evaluatorId, "it-team");
        assertNull(evaluatorLogic.getEvaluator(evaluatorId, "it-team"));
        assertNull(evaluatorLogic.getEvaluatorDraft(evaluatorId, "it-team"));
    }

    @Test
    void deleteEvaluatorDraftRequiresExactIdentity() {
        String evaluatorId = newId();
        String draftId = newId();
        evaluatorLogic.saveEvaluatorWithDraft(evaluatorOf(evaluatorId, T0, T0),
                draftOf(draftId, evaluatorId, T0, T0));

        assertEquals("expected Evaluator draft does not exist",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic
                        .deleteEvaluatorDraft(evaluatorId, "missing", "it-team")).getMessage());
        assertEquals("expected Evaluator draft does not exist",
                assertThrows(IllegalArgumentException.class, () -> evaluatorLogic
                        .deleteEvaluatorDraft(evaluatorId, draftId, "other-team")).getMessage());

        evaluatorLogic.deleteEvaluatorDraft(evaluatorId, draftId, "it-team");
        assertNull(evaluatorLogic.getEvaluatorDraft(evaluatorId, "it-team"));
    }
}
