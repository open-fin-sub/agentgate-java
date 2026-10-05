package com.abchina.llmalf.agentgate.domain.model.run;

import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersionStatus;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.domain.model.gate.ReleaseGateSpec;
import com.abchina.llmalf.agentgate.domain.model.metric.MetricPlan;
import com.abchina.llmalf.agentgate.domain.model.target.TargetRef;
import com.abchina.llmalf.agentgate.domain.model.target.TargetSnapshot;
import com.abchina.llmalf.agentgate.domain.model.target.TargetType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RunManifest 聚焦行为测试(golden 未覆盖部分).
 */
class RunManifestTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");

    private static Case caseOf(String id) {
        Map<String, Object> input = new HashMap<>();
        input.put("q", id);
        return com.abchina.llmalf.agentgate.domain.model.cases.Case.of(
                id, "用例-" + id,
                Collections.singletonList(
                        com.abchina.llmalf.agentgate.domain.model.cases.CaseTurn.of(id + "-t1", input)));
    }

    private static DatasetVersion publishedDataset(Case... cases) {
        return DatasetVersion.of("dv-1", "ds-1", "", "", 1, DatasetVersionStatus.PUBLISHED,
                null, Arrays.asList(cases), "", T0, T0, T0, "", "", "", "");
    }

    private static TargetSnapshot targetSnapshot() {
        return TargetSnapshot.of(
                TargetRef.of("demo", TargetType.AGENT, "loan", "v1"),
                "信贷助手", "python_function", "1",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                null, null, T0, "");
    }

    private static EvaluatorSpec spec(String id) {
        return EvaluatorSpec.of(id, id, "1", null, "state", id + "_metric", null,
                "final_state", null, null, null, null, "", "", "", "");
    }

    @Test
    void convenienceFactoryAppliesDefaults() {
        RunManifest manifest = RunManifest.of(publishedDataset(caseOf("c1")),
                targetSnapshot(), Collections.singletonList(spec("state")),
                Collections.singletonList("state"), MetricPlan.of(), ReleaseGateSpec.of());
        assertEquals(300.0, manifest.timeoutSeconds(), 0.0);
        assertEquals(0, manifest.maxRetries());
        assertEquals(1, manifest.maxParallelCases());
        assertEquals(null, manifest.selectedCaseIds());
        assertTrue(manifest.manifestSha256().matches("[0-9a-f]{64}"));
    }

    @Test
    void executionCasesFollowSelectionOrder() {
        DatasetVersion dataset = publishedDataset(caseOf("c1"), caseOf("c2"), caseOf("c3"));
        TargetSnapshot target = targetSnapshot();
        List<EvaluatorSpec> specs = Collections.singletonList(spec("state"));

        RunManifest unselected = RunManifest.of(dataset, null, target, specs,
                Collections.singletonList("state"), MetricPlan.of(), ReleaseGateSpec.of());
        assertEquals(3, unselected.executionCases().size());
        assertEquals("c1", unselected.executionCases().get(0).id());

        RunManifest selected = RunManifest.of(dataset, Arrays.asList("c3", "c1"), target,
                specs, Collections.singletonList("state"), MetricPlan.of(), ReleaseGateSpec.of());
        assertEquals(2, selected.executionCases().size());
        assertEquals("c3", selected.executionCases().get(0).id());
        assertEquals("c1", selected.executionCases().get(1).id());
    }

    @Test
    void executionCasesListIsImmutable() {
        RunManifest manifest = RunManifest.of(publishedDataset(caseOf("c1")),
                targetSnapshot(), Collections.singletonList(spec("state")),
                Collections.singletonList("state"), MetricPlan.of(), ReleaseGateSpec.of());
        assertThrows(UnsupportedOperationException.class,
                () -> manifest.executionCases().clear());
    }

    @Test
    void manifestHashSemantics() {
        DatasetVersion dataset = publishedDataset(caseOf("c1"), caseOf("c2"));
        TargetSnapshot target = targetSnapshot();
        List<EvaluatorSpec> specs = Collections.singletonList(spec("state"));

        RunManifest first = RunManifest.of(dataset, null, target, specs,
                Collections.singletonList("state"), MetricPlan.of(), ReleaseGateSpec.of(),
                300, 0, 1, T0, "");
        RunManifest later = RunManifest.of(dataset, null, target, specs,
                Collections.singletonList("state"), MetricPlan.of(), ReleaseGateSpec.of(),
                300, 0, 1, T0.plusDays(5), "");
        assertEquals(first.manifestSha256(), later.manifestSha256(),
                "created_at must not affect the manifest hash");

        RunManifest differentTimeout = RunManifest.of(dataset, null, target, specs,
                Collections.singletonList("state"), MetricPlan.of(), ReleaseGateSpec.of(),
                120, 0, 1, T0, "");
        assertNotEquals(first.manifestSha256(), differentTimeout.manifestSha256(),
                "timeout must affect the manifest hash");

        RunManifest explicit = RunManifest.of(dataset, null, target, specs,
                Collections.singletonList("state"), MetricPlan.of(), ReleaseGateSpec.of(),
                300, 0, 1, T0, first.manifestSha256());
        assertEquals(first.manifestSha256(), explicit.manifestSha256());
    }

    @Test
    void primaryAndSpecListsAreImmutable() {
        List<EvaluatorSpec> specSource = new ArrayList<>(Collections.singletonList(spec("state")));
        RunManifest manifest = RunManifest.of(publishedDataset(caseOf("c1")),
                targetSnapshot(), specSource,
                Collections.singletonList("state"), MetricPlan.of(), ReleaseGateSpec.of());
        assertThrows(UnsupportedOperationException.class,
                () -> manifest.evaluatorSpecs().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> manifest.primaryEvaluatorIds().add("other"));
        specSource.clear();
        assertEquals(1, manifest.evaluatorSpecs().size());
    }

    @Test
    void unknownIdentifiersAreSortedInMessage() {
        DatasetVersion dataset = publishedDataset(caseOf("c1"));
        assertEquals("selected_case_ids reference unknown Cases: c8, c9",
                assertThrows(IllegalArgumentException.class, () -> RunManifest.of(
                        dataset, Arrays.asList("c9", "c8"), targetSnapshot(),
                        Collections.singletonList(spec("state")),
                        Collections.singletonList("state"), MetricPlan.of(),
                        ReleaseGateSpec.of())).getMessage());
        assertEquals("primary_evaluator_ids reference unknown Evaluators: m1, m2",
                assertThrows(IllegalArgumentException.class, () -> RunManifest.of(
                        dataset, null, targetSnapshot(),
                        Collections.singletonList(spec("state")),
                        Arrays.asList("m2", "m1"), MetricPlan.of(),
                        ReleaseGateSpec.of())).getMessage());
    }

    @Test
    void nullRequiredObjectsAreRejected() {
        DatasetVersion dataset = publishedDataset(caseOf("c1"));
        assertEquals("Field required",
                assertThrows(IllegalArgumentException.class, () -> RunManifest.of(
                        null, null, targetSnapshot(),
                        Collections.singletonList(spec("state")),
                        Collections.singletonList("state"), MetricPlan.of(),
                        ReleaseGateSpec.of())).getMessage());
        assertEquals("Field required",
                assertThrows(IllegalArgumentException.class, () -> RunManifest.of(
                        dataset, null, null,
                        Collections.singletonList(spec("state")),
                        Collections.singletonList("state"), MetricPlan.of(),
                        ReleaseGateSpec.of())).getMessage());
    }
}
