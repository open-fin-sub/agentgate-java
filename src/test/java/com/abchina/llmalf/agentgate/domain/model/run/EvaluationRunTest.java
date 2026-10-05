package com.abchina.llmalf.agentgate.domain.model.run;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EvaluationRun 组合与迁移行为测试(golden 未覆盖部分).
 *
 * <p>用最小 stub manifest 桩化清单字段,聚焦 Run 本体的组合/透传/payload 拍平语义。</p>
 */
class EvaluationRunTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");

    private static RunManifest stubManifest() {
        return RunManifestStub.minimal();
    }

    @Test
    void compositionDelegatesLifecycleValidation() {
        assertEquals("running EvaluationRun requires only started_at",
                assertThrows(IllegalArgumentException.class, () -> EvaluationRun.of(
                        "run-1", stubManifest(),
                        RunLifecycle.of(RunStatus.RUNNING, T0, null, null, null, null),
                        "", "", "", null, 0)).getMessage());
        assertEquals("Field required",
                assertThrows(IllegalArgumentException.class, () -> EvaluationRun.of(
                        "run-1", null,
                        RunLifecycle.of(RunStatus.PENDING, T0, null, null, null, null),
                        "", "", "", null, 0)).getMessage());
        assertEquals("EvaluationRun id must not be blank",
                assertThrows(IllegalArgumentException.class, () -> EvaluationRun.of(
                        " ", stubManifest(),
                        RunLifecycle.of(RunStatus.PENDING, T0, null, null, null, null),
                        "", "", "", null, 0)).getMessage());
        assertEquals("Input should be greater than or equal to 0",
                assertThrows(IllegalArgumentException.class, () -> EvaluationRun.of(
                        "run-1", stubManifest(),
                        RunLifecycle.of(RunStatus.PENDING, T0, null, null, null, null),
                        "", "", "", null, -1)).getMessage());
    }

    @Test
    void transitionDelegatesToStateMachineAndKeepsOtherFields() {
        EvaluationRun pending = EvaluationRun.of("run-1", stubManifest(),
                RunLifecycle.of(RunStatus.PENDING, T0, null, null, null, null),
                "team", "user", "张三", "sk-1", 2);

        EvaluationRun running = pending.transition(RunStatus.RUNNING, T1, null);
        assertEquals(RunStatus.RUNNING, running.status());
        assertEquals(T1, running.startedAt());
        assertEquals("run-1", running.id());
        assertSame(pending.manifest(), running.manifest());
        assertEquals("team", running.userTeamId());
        assertEquals("sk-1", running.apiKey());
        assertEquals(2, running.dispatchAttempts());

        EvaluationRun failed = running.transition(RunStatus.FAILED, T1, "boom");
        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals("boom", failed.error());
        assertEquals(2, failed.dispatchAttempts(), "dispatch_attempts must pass through");

        assertEquals("illegal Run transition: failed -> running",
                assertThrows(IllegalArgumentException.class,
                        () -> failed.transition(RunStatus.RUNNING, T1, null)).getMessage());
    }

    @Test
    void toPayloadFlattensLifecycleFieldsInDeclarationOrder() {
        EvaluationRun run = EvaluationRun.of("run-1", stubManifest(),
                RunLifecycle.of(RunStatus.RUNNING, T0, null, T1, null, null),
                "t", "u", "n", "sk-9", 3);
        Map<?, ?> payload = (Map<?, ?>) run.toPayload();
        assertEquals("run-1", payload.get("id"));
        assertEquals("running", payload.get("status"));
        assertEquals("2026-01-01T00:00:00Z", payload.get("created_at"));
        assertNull(payload.get("scheduled_for"));
        assertEquals("2026-01-02T00:00:00Z", payload.get("started_at"));
        assertNull(payload.get("completed_at"));
        assertNull(payload.get("error"));
        assertEquals("t", payload.get("user_team_id"));
        assertEquals("u", payload.get("user_id"));
        assertEquals("n", payload.get("user_name"));
        assertEquals("sk-9", payload.get("api_key"));
        assertEquals(3, payload.get("dispatch_attempts"));
        assertTrue(payload.get("manifest") instanceof Map, "manifest nests its own payload");
        assertEquals(13, payload.size(), "payload must contain all 13 fields");
    }

    @Test
    void fromPayloadRebuildsLifecycleAndDefaults() {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", "run-9");
        payload.put("manifest", RunManifestStub.minimalPayload());
        payload.put("status", "pending");
        payload.put("created_at", "2026-01-01T00:00:00Z");
        EvaluationRun run = EvaluationRun.fromPayload(payload);
        assertEquals("run-9", run.id());
        assertEquals(RunStatus.PENDING, run.status());
        assertEquals(T0, run.createdAt());
        assertEquals("", run.userTeamId());
        assertNull(run.apiKey());
        assertEquals(0, run.dispatchAttempts());
    }

    private static final class RunManifestStub {
        private static RunManifest minimal() {
            return RunManifest.fromPayload(minimalPayload());
        }

        private static Map<String, Object> minimalPayload() {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("dataset", datasetPayload());
            payload.put("target", targetPayload());
            payload.put("evaluator_specs", Collections.singletonList(specPayload()));
            payload.put("primary_evaluator_ids", Collections.singletonList("state"));
            payload.put("metric_plan", new java.util.HashMap<>());
            payload.put("gate_spec", new java.util.HashMap<>());
            payload.put("created_at", "2026-01-01T00:00:00Z");
            return payload;
        }

        private static Map<String, Object> datasetPayload() {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("id", "dv-1");
            payload.put("dataset_id", "ds-1");
            payload.put("version", 1);
            payload.put("status", "published");
            payload.put("cases", Collections.singletonList(casePayload()));
            payload.put("created_at", "2026-01-01T00:00:00Z");
            payload.put("updated_at", "2026-01-01T00:00:00Z");
            payload.put("published_at", "2026-01-01T00:00:00Z");
            return payload;
        }

        private static Map<String, Object> casePayload() {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("id", "c1");
            payload.put("name", "用例-c1");
            Map<String, Object> turn = new java.util.LinkedHashMap<>();
            turn.put("id", "c1-t1");
            turn.put("input", Collections.singletonMap("q", "c1"));
            payload.put("turns", Collections.singletonList(turn));
            return payload;
        }

        private static Map<String, Object> targetPayload() {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            Map<String, Object> ref = new java.util.LinkedHashMap<>();
            ref.put("source_id", "demo");
            ref.put("target_type", "agent");
            ref.put("external_target_id", "loan");
            ref.put("external_version_id", "v1");
            payload.put("ref", ref);
            payload.put("display_name", "信贷助手");
            payload.put("adapter_type", "python_function");
            payload.put("adapter_version", "1");
            payload.put("descriptor_sha256",
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            return payload;
        }

        private static Map<String, Object> specPayload() {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("id", "state");
            payload.put("name", "state");
            payload.put("dimension", "state");
            payload.put("metric", "state_metric");
            payload.put("implementation_id", "final_state");
            return payload;
        }
    }
}
