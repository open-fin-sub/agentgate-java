package com.abchina.llmalf.agentgate.domain.model.expectation;

import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Condition/Expectation 构造与解析行为测试(golden 未覆盖部分).
 *
 * <p>覆盖:非有限浮点、冻结不可变、默认值与 id 生成、未知 kind 分发、
 * 容器值 Equals 的 Java 侧序列化能力(Python 侧为已知限制)。</p>
 */
class ConditionsTest {

    @Test
    void equalsFreezesContainerValues() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("a", 1);
        Equals equals = Equals.of(nested);
        Map<String, Object> frozen = (Map<String, Object>) equals.expected();
        assertThrows(UnsupportedOperationException.class, () -> frozen.put("b", 2));
        nested.put("late", 1);
        assertTrue(!frozen.containsKey("late"), "of must copy the source value");
        assertEquals("{\"expected\":{\"a\":1},\"kind\":\"equals\"}",
                CanonicalJson.serialize(equals.toPayload()));
    }

    @Test
    void equalsRejectsNonFiniteNumbers() {
        IllegalArgumentException nestedInf = assertThrows(IllegalArgumentException.class,
                () -> Equals.of(Arrays.asList(1, Double.POSITIVE_INFINITY)));
        assertEquals("JSON numbers must be finite", nestedInf.getMessage());

        IllegalArgumentException nan = assertThrows(IllegalArgumentException.class,
                () -> Equals.of(Double.NaN));
        assertEquals("JSON numbers must be finite", nan.getMessage());

        IllegalArgumentException unknownType = assertThrows(IllegalArgumentException.class,
                () -> Equals.of(new Object()));
        assertEquals("value is not JSON-compatible: Object", unknownType.getMessage());
    }

    @Test
    void withinToleranceRejectsNonFiniteAndNonPositiveEpsilon() {
        assertEquals("Input should be a finite number",
                assertThrows(IllegalArgumentException.class,
                        () -> WithinTolerance.of(Double.POSITIVE_INFINITY, 1)).getMessage());
        assertEquals("Input should be a finite number",
                assertThrows(IllegalArgumentException.class,
                        () -> WithinTolerance.of(1, Double.NaN)).getMessage());
        assertEquals("Input should be greater than 0",
                assertThrows(IllegalArgumentException.class,
                        () -> WithinTolerance.of(1, 0)).getMessage());
        assertEquals("Input should be greater than 0",
                assertThrows(IllegalArgumentException.class,
                        () -> WithinTolerance.of(1, -0.001)).getMessage());
    }

    @Test
    void withinRangeRejectsNonFiniteBounds() {
        assertEquals("Input should be a finite number",
                assertThrows(IllegalArgumentException.class,
                        () -> WithinRange.of(Double.NEGATIVE_INFINITY, 1.0)).getMessage());
        assertEquals("Input should be a finite number",
                assertThrows(IllegalArgumentException.class,
                        () -> WithinRange.of(null, Double.POSITIVE_INFINITY)).getMessage());
    }

    @Test
    void oneOfAllowedIsImmutableAndFrozen() {
        OneOf oneOf = OneOf.of(Arrays.asList("a", "b"));
        assertThrows(UnsupportedOperationException.class, () -> oneOf.allowed().add("c"));

        List<Object> source = new ArrayList<>();
        source.add(Arrays.asList(1, 2));
        OneOf nested = OneOf.of(source);
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) nested.allowed().get(0)).add(3));
    }

    @Test
    void mustBeMissingIsStatelessSingleton() {
        assertSame(MustBeMissing.of(), MustBeMissing.of());
        assertEquals("{\"kind\":\"must_be_missing\"}",
                CanonicalJson.serialize(MustBeMissing.of().toPayload()));
    }

    @Test
    void matchesPatternPassesThroughPythonRegexSyntax() {
        MatchesPattern pythonNamedGroup = MatchesPattern.of("(?P<name>\\d+)");
        assertEquals("(?P<name>\\d+)", pythonNamedGroup.pattern());
        assertEquals("{\"kind\":\"matches_pattern\",\"pattern\":\"(?P<name>\\\\d+)\"}",
                CanonicalJson.serialize(pythonNamedGroup.toPayload()));

        MatchesPattern empty = MatchesPattern.of("");
        assertEquals("", empty.pattern());
    }

    @Test
    void matchesPatternNullIsRejected() {
        assertEquals("Field required",
                assertThrows(IllegalArgumentException.class, () -> MatchesPattern.of(null))
                        .getMessage());
    }

    @Test
    void matchesJsonSchemaFreezesAndRejectsNull() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        MatchesJsonSchema condition = MatchesJsonSchema.of(schema);
        assertThrows(UnsupportedOperationException.class,
                () -> condition.jsonSchema().put("extra", 1));
        assertEquals("Field required",
                assertThrows(IllegalArgumentException.class, () -> MatchesJsonSchema.of(null))
                        .getMessage());
    }

    @Test
    void unknownKindsAreRejected() {
        Map<String, Object> badCondition = new HashMap<>();
        badCondition.put("kind", "mystery");
        assertEquals("unknown condition kind: mystery",
                assertThrows(IllegalArgumentException.class,
                        () -> Condition.fromPayload(badCondition)).getMessage());
        assertEquals("unknown condition kind: null",
                assertThrows(IllegalArgumentException.class,
                        () -> Condition.fromPayload(new HashMap<String, Object>())).getMessage());

        Map<String, Object> badExpectation = new HashMap<>();
        badExpectation.put("kind", "mystery");
        assertEquals("unknown expectation kind: mystery",
                assertThrows(IllegalArgumentException.class,
                        () -> Expectation.fromPayload(badExpectation)).getMessage());
        assertEquals("unknown expectation kind: null",
                assertThrows(IllegalArgumentException.class,
                        () -> Expectation.fromPayload(new HashMap<String, Object>())).getMessage());
    }

    @Test
    void fromPayloadAppliesDefaults() {
        Map<String, Object> tolerance = new HashMap<>();
        tolerance.put("kind", "within_tolerance");
        tolerance.put("expected", 1);
        WithinTolerance parsed = (WithinTolerance) Condition.fromPayload(tolerance);
        assertEquals(WithinTolerance.DEFAULT_EPSILON, parsed.epsilon(), 0.0);

        Map<String, Object> toolCall = new HashMap<>();
        toolCall.put("kind", "tool_call");
        toolCall.put("tool", "search");
        ToolCallExpectation call = (ToolCallExpectation) Expectation.fromPayload(toolCall);
        assertEquals(ToolCallExpectation.MODE_REQUIRED, call.mode());

        Map<String, Object> toolArgument = new HashMap<>();
        toolArgument.put("kind", "tool_argument");
        toolArgument.put("tool", "search");
        toolArgument.put("path", "q");
        toolArgument.put("condition", java.util.Collections.singletonMap("kind", "must_be_missing"));
        ToolArgumentExpectation argument = (ToolArgumentExpectation) Expectation.fromPayload(toolArgument);
        assertEquals(ToolArgumentExpectation.OCCURRENCE_LAST, argument.occurrence());
    }

    @Test
    void fromPayloadGeneratesIdWhenMissing() {
        Map<String, Object> policy = new HashMap<>();
        policy.put("kind", "policy");
        policy.put("policy_id", "p1");
        PolicyExpectation first = (PolicyExpectation) Expectation.fromPayload(policy);
        PolicyExpectation second = (PolicyExpectation) Expectation.fromPayload(policy);
        assertTrue(first.id().trim().length() > 0, "generated id must not be blank");
        assertTrue(!first.id().equals(second.id()), "generated ids must differ");
    }

    @Test
    void expectationValidatesCommonFields() {
        assertEquals("Expectation id must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> PolicyExpectation.of(" ", null, "p")).getMessage());
        assertEquals("Expectation name must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> PolicyExpectation.of("e1", "  ", "p")).getMessage());
        assertEquals("Expectation id must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> PolicyExpectation.of(null, null, "p")).getMessage());
    }
}
