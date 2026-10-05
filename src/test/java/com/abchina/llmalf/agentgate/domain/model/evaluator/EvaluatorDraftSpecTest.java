package com.abchina.llmalf.agentgate.domain.model.evaluator;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EvaluatorDraft/EvaluatorSpec 组合与配置校验测试(golden 未覆盖部分).
 */
class EvaluatorDraftSpecTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");

    private static List<EvaluatorRef> children(EvaluatorRef... refs) {
        return new ArrayList<>(Arrays.asList(refs));
    }

    @Test
    void draftDefaultsAndImmutability() {
        EvaluatorDraft draft = EvaluatorDraft.of("d1", "e1", null, null, "state", "metric",
                null, "impl", null, null, null, null, T0, T0, null, null, null);
        assertEquals(EvaluatorKind.RULE, draft.kind());
        assertEquals(EvaluatorSeverity.STANDARD, draft.severity());
        assertEquals("1", draft.implementationVersion());
        assertTrue(draft.config().isEmpty());
        assertTrue(draft.children().isEmpty());
        assertEquals(null, draft.combination());
        assertThrows(UnsupportedOperationException.class,
                () -> draft.children().add(EvaluatorRef.of("x", "1", null)));
    }

    @Test
    void draftConfigIsFrozen() {
        Map<String, Object> config = new HashMap<>();
        config.put("threshold", 0.5);
        EvaluatorDraft draft = EvaluatorDraft.of("d1", "e1", null, null, "d", "m",
                null, "i", null, config, null, null, T0, T0, null, null, null);
        assertThrows(UnsupportedOperationException.class, () -> draft.config().put("late", 1));
        config.put("late", 1);
        assertTrue(!draft.config().containsKey("late"), "of must copy the config");
    }

    @Test
    void draftConfigRejectsNonFiniteNumbers() {
        Map<String, Object> config = new HashMap<>();
        config.put("x", Double.POSITIVE_INFINITY);
        assertEquals("JSON numbers must be finite",
                assertThrows(IllegalArgumentException.class, () -> EvaluatorDraft.of(
                        "d1", "e1", null, null, "d", "m", null, "i", null,
                        config, null, null, T0, T0, null, null, null)).getMessage());
    }

    @Test
    void specHashExcludesUserFieldsButIncludesDefinition() {
        EvaluatorSpec first = EvaluatorSpec.of("s1", "n", "1", null, "d", "m",
                null, "i", null, null, null, null, "", "t1", "u1", "名1");
        EvaluatorSpec second = EvaluatorSpec.of("s1", "n", "1", null, "d", "m",
                null, "i", null, null, null, null, "", "t2", "u2", "名2");
        assertEquals(first.contentSha256(), second.contentSha256(),
                "user fields must not affect the hash");

        EvaluatorSpec otherMetric = EvaluatorSpec.of("s1", "n", "1", null, "d", "m2",
                null, "i", null, null, null, null, "", "", "", "");
        assertTrue(!first.contentSha256().equals(otherMetric.contentSha256()),
                "metric must affect the hash");
    }

    @Test
    void specExplicitHashFlow() {
        EvaluatorSpec base = EvaluatorSpec.of("s1", "n", "1", EvaluatorKind.HYBRID, "d", "m",
                EvaluatorSeverity.BLOCKING, "i", "9", null,
                children(EvaluatorRef.of("a", "1", 0.6), EvaluatorRef.of("b", "2", 0.4)),
                CombinationPolicy.WEIGHTED_SCORE, "", "", "", "");
        EvaluatorSpec verified = EvaluatorSpec.of("s1", "n", "1", EvaluatorKind.HYBRID, "d", "m",
                EvaluatorSeverity.BLOCKING, "i", "9", null,
                children(EvaluatorRef.of("a", "1", 0.6), EvaluatorRef.of("b", "2", 0.4)),
                CombinationPolicy.WEIGHTED_SCORE, base.contentSha256(), "", "", "");
        assertEquals(base.contentSha256(), verified.contentSha256());
        assertTrue(base.contentSha256().matches("[0-9a-f]{64}"));
    }

    @Test
    void specHashFormatAndMismatch() {
        assertEquals("content_sha256 must be a lowercase SHA-256 digest",
                assertThrows(IllegalArgumentException.class, () -> EvaluatorSpec.of(
                        "s1", "n", "1", null, "d", "m", null, "i", null, null, null, null,
                        "xyz", "", "", "")).getMessage());
        assertEquals("content_sha256 must be a lowercase SHA-256 digest",
                assertThrows(IllegalArgumentException.class, () -> EvaluatorSpec.of(
                        "s1", "n", "1", null, "d", "m", null, "i", null, null, null, null,
                        "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789",
                        "", "", "")).getMessage());
        assertEquals("EvaluatorSpec content hash mismatch",
                assertThrows(IllegalArgumentException.class, () -> EvaluatorSpec.of(
                        "s1", "n", "1", null, "d", "m", null, "i", null, null, null, null,
                        "0000000000000000000000000000000000000000000000000000000000000000",
                        "", "", "")).getMessage());
    }

    @Test
    void specChildrenListIsImmutable() {
        List<EvaluatorRef> source = children(EvaluatorRef.of("a", "1", null),
                EvaluatorRef.of("b", "1", null));
        EvaluatorSpec spec = EvaluatorSpec.of("s1", "n", "1", EvaluatorKind.HYBRID, "d", "m",
                null, "i", null, null, source, CombinationPolicy.ALL, "", "", "", "");
        assertThrows(UnsupportedOperationException.class, () -> spec.children().remove(0));
        source.clear();
        assertEquals(2, spec.children().size(), "of must copy the children list");
    }

    @Test
    void sameChildDifferentVersionAllowed() {
        EvaluatorSpec spec = EvaluatorSpec.of("s1", "n", "1", EvaluatorKind.HYBRID, "d", "m",
                null, "i", null, null,
                children(EvaluatorRef.of("a", "1", null), EvaluatorRef.of("a", "2", null)),
                CombinationPolicy.ANY, "", "", "", "");
        assertEquals(2, spec.children().size());
    }

    @Test
    void fromPayloadParsesNestedStructures() {
        Map<String, Object> model = new HashMap<>();
        model.put("provider_id", "openai");
        model.put("model_id", "gpt-4o");
        Map<String, Object> config = new HashMap<>();
        config.put("model", model);
        Map<String, Object> child = new HashMap<>();
        child.put("evaluator_id", "a");
        child.put("evaluator_version", "2");
        child.put("weight", 0.7);
        Map<String, Object> secondChild = new HashMap<>();
        secondChild.put("evaluator_id", "b");
        secondChild.put("evaluator_version", "1");
        secondChild.put("weight", 0.3);
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "s1");
        payload.put("name", "n");
        payload.put("kind", "hybrid");
        payload.put("dimension", "d");
        payload.put("metric", "m");
        payload.put("implementation_id", "i");
        payload.put("config", config);
        payload.put("children", Arrays.asList(child, secondChild));
        payload.put("combination", "weighted_score");

        EvaluatorSpec spec = EvaluatorSpec.fromPayload(payload);
        assertEquals(EvaluatorKind.HYBRID, spec.kind());
        assertEquals(CombinationPolicy.WEIGHTED_SCORE, spec.combination());
        assertEquals(2, spec.children().size());
        assertEquals(Double.valueOf(0.7), spec.children().get(0).weight());
        assertEquals("openai", ((Map<?, ?>) spec.config().get("model")).get("provider_id"));
        assertTrue(spec.contentSha256().matches("[0-9a-f]{64}"));
    }

    @Test
    void fromPayloadAppliesDraftDefaults() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("evaluator_id", "e1");
        payload.put("dimension", "d");
        payload.put("metric", "m");
        payload.put("implementation_id", "i");
        payload.put("created_at", "2026-01-01T00:00:00+00:00");
        payload.put("updated_at", "2026-01-01T00:00:00+00:00");
        EvaluatorDraft draft = EvaluatorDraft.fromPayload(payload);
        assertTrue(draft.id().trim().length() > 0);
        assertEquals(EvaluatorKind.RULE, draft.kind());
        assertEquals(EvaluatorSeverity.STANDARD, draft.severity());
        assertEquals("1", draft.implementationVersion());
        assertEquals(T0, draft.createdAt());
    }
}
