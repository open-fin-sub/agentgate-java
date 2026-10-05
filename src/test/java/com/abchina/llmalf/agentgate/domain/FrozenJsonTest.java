package com.abchina.llmalf.agentgate.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FrozenJson 冻结/解冻语义测试.
 *
 * <p>对齐 Python domain/base.py::freeze_json/thaw_json/FrozenJsonObject:
 * 构造期校验(非 String 键、非有限浮点、非 JSON 类型)与不可变/深拷贝语义。</p>
 */
class FrozenJsonTest {

    @Test
    void nonStringKeysAreRejectedOnFreezeAndThaw() {
        Map<Object, Object> map = new HashMap<>();
        map.put("a", 1);
        map.put(7, "x");
        IllegalArgumentException freezeError = assertThrows(IllegalArgumentException.class,
                () -> FrozenJson.freeze(map));
        assertEquals("JSON object keys must be strings", freezeError.getMessage());

        IllegalArgumentException thawError = assertThrows(IllegalArgumentException.class,
                () -> FrozenJson.thaw(map));
        assertEquals("JSON object keys must be strings", thawError.getMessage());
    }

    @Test
    void nonFiniteFloatsAreRejected() {
        IllegalArgumentException nan = assertThrows(IllegalArgumentException.class,
                () -> FrozenJson.freeze(Double.NaN));
        assertEquals("JSON numbers must be finite", nan.getMessage());

        List<Object> nested = new ArrayList<>();
        nested.add(Double.POSITIVE_INFINITY);
        IllegalArgumentException nestedError = assertThrows(IllegalArgumentException.class,
                () -> FrozenJson.freeze(nested));
        assertEquals("JSON numbers must be finite", nestedError.getMessage());

        assertThrows(IllegalArgumentException.class, () -> FrozenJson.freeze(Float.NEGATIVE_INFINITY));
    }

    @Test
    void nonJsonTypesAreRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FrozenJson.freeze(new Object()));
        assertEquals("value is not JSON-compatible: Object", error.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void frozenContainersRejectMutation() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("a", 1);
        List<Object> inner = new ArrayList<>();
        inner.add("x");
        source.put("list", inner);

        Map<String, Object> frozen = (Map<String, Object>) FrozenJson.freeze(source);
        assertThrows(UnsupportedOperationException.class, () -> frozen.put("b", 2));
        assertThrows(UnsupportedOperationException.class, () -> frozen.remove("a"));
        List<Object> frozenInner = (List<Object>) frozen.get("list");
        assertThrows(UnsupportedOperationException.class, () -> frozenInner.add("y"));

        source.put("late", "mutated-after-freeze");
        assertTrue(!frozen.containsKey("late"), "freeze must copy the source structure");
    }

    @Test
    @SuppressWarnings("unchecked")
    void thawReturnsIndependentMutableDeepCopy() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("a", 1);
        List<Object> inner = new ArrayList<>();
        inner.add("x");
        source.put("list", inner);

        Map<String, Object> frozen = (Map<String, Object>) FrozenJson.freeze(source);
        Map<String, Object> thawed = (Map<String, Object>) FrozenJson.thaw(frozen);
        assertEquals(frozen, thawed);

        thawed.put("b", 2);
        ((List<Object>) thawed.get("list")).add("y");
        assertTrue(!frozen.containsKey("b"), "thaw must not affect the frozen tree");
        assertEquals(1, ((List<Object>) frozen.get("list")).size());
    }

    @Test
    void scalarsPassThroughUnchanged() {
        assertSame(null, FrozenJson.freeze(null));
        assertEquals("text", FrozenJson.freeze("text"));
        assertEquals(Boolean.TRUE, FrozenJson.freeze(Boolean.TRUE));
        assertEquals(42, FrozenJson.freeze(42));
        assertEquals(-0.5d, FrozenJson.freeze(-0.5d));
        assertEquals("same", FrozenJson.thaw("same"));
        assertEquals(7, FrozenJson.thaw(7));
    }

    @Test
    void freezingPreservesCanonicalForm() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("agent", "评测");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("accuracy", 0.95);
        metrics.put("loss", 1e-7);
        source.put("metrics", metrics);
        List<Object> tags = new ArrayList<>();
        tags.add("中文");
        tags.add("\ud83d\ude80");
        source.put("tags", tags);

        String before = CanonicalJson.serialize(source);
        String frozen = CanonicalJson.serialize(FrozenJson.freeze(source));
        String thawed = CanonicalJson.serialize(FrozenJson.thaw(FrozenJson.freeze(source)));
        assertEquals(before, frozen);
        assertEquals(before, thawed);
        assertNotEquals(0, ContentSha256.of(FrozenJson.freeze(source)).length());
    }
}
