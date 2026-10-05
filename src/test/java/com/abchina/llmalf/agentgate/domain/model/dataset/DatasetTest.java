package com.abchina.llmalf.agentgate.domain.model.dataset;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dataset 构造与解析行为测试(golden 未覆盖部分).
 */
class DatasetTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");

    @Test
    void ofAppliesDefaultsForOptionalFields() {
        Dataset dataset = Dataset.of("d1", "名称", null, false, T0, T0, null, null, null);
        assertEquals("", dataset.description());
        assertEquals("", dataset.userTeamId());
        assertEquals("", dataset.userId());
        assertEquals("", dataset.userName());
        assertEquals(false, dataset.archived());
    }

    @Test
    void nullTimestampsFallBackToUtcNow() {
        Dataset dataset = Dataset.of("d1", "名称", "", false, null, null, "", "", "");
        assertEquals(ZoneOffset.UTC, dataset.createdAt().getOffset());
        assertEquals(ZoneOffset.UTC, dataset.updatedAt().getOffset());
        assertTrue(!dataset.updatedAt().isBefore(dataset.createdAt()));
    }

    @Test
    void timestampsNormalizeToUtc() {
        OffsetDateTime beijing = OffsetDateTime.of(2026, 1, 1, 8, 0, 0, 0, ZoneOffset.ofHours(8));
        Dataset dataset = Dataset.of("d1", "名称", "", false, beijing, beijing, "", "", "");
        assertEquals(ZoneOffset.UTC, dataset.createdAt().getOffset());
        assertEquals(T0, dataset.createdAt());
    }

    @Test
    void identityValidation() {
        assertEquals("Dataset id must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> Dataset.of(" ", "n", "", false, T0, T0, "", "", "")).getMessage());
        assertEquals("Dataset name must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> Dataset.of("d1", " ", "", false, T0, T0, "", "", "")).getMessage());
    }

    @Test
    void updatedBeforeCreatedIsRejected() {
        assertEquals("updated_at must not precede created_at",
                assertThrows(IllegalArgumentException.class,
                        () -> Dataset.of("d1", "n", "", false, T1, T0, "", "", "")).getMessage());
    }

    @Test
    void isoFormatMatchesPydanticSerialization() {
        assertEquals("2026-01-01T00:00:00Z", DomainValidations.isoFormat(T0));
        OffsetDateTime micro = OffsetDateTime.parse("2026-01-01T00:00:00.123456+00:00");
        assertEquals("2026-01-01T00:00:00.123456Z", DomainValidations.isoFormat(micro));
        OffsetDateTime microOne = OffsetDateTime.parse("2026-01-01T00:00:00.000001+00:00");
        assertEquals("2026-01-01T00:00:00.000001Z", DomainValidations.isoFormat(microOne));
        OffsetDateTime nano = OffsetDateTime.parse("2026-01-01T00:00:00.123456789+00:00");
        assertEquals("2026-01-01T00:00:00.123456Z", DomainValidations.isoFormat(nano));
    }

    @Test
    void fromPayloadGeneratesIdAndTimestamps() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "新数据集");
        Dataset first = Dataset.fromPayload(payload);
        Dataset second = Dataset.fromPayload(payload);
        assertTrue(first.id().trim().length() > 0, "generated id must not be blank");
        assertTrue(!first.id().equals(second.id()), "generated ids must differ");
        assertTrue(!first.createdAt().isBefore(
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
        assertTrue(!first.updatedAt().isBefore(first.createdAt()));
    }

    @Test
    void fromPayloadRejectsBadTypes() {
        Map<String, Object> badArchived = new HashMap<>();
        badArchived.put("id", "d1");
        badArchived.put("name", "n");
        badArchived.put("archived", "yes");
        assertEquals("Input should be a valid boolean",
                assertThrows(IllegalArgumentException.class, () -> Dataset.fromPayload(badArchived))
                        .getMessage());

        Map<String, Object> badCreated = new HashMap<>();
        badCreated.put("id", "d1");
        badCreated.put("name", "n");
        badCreated.put("created_at", 12345);
        assertEquals("Input should be a valid datetime",
                assertThrows(IllegalArgumentException.class, () -> Dataset.fromPayload(badCreated))
                        .getMessage());
    }

    @Test
    void toPayloadUsesZSuffixTimestamps() {
        Dataset dataset = Dataset.of("d1", "名称", "描述", true, T0, T1, "t", "u", "张三");
        Map<?, ?> payload = (Map<?, ?>) dataset.toPayload();
        assertEquals("d1", payload.get("id"));
        assertEquals("名称", payload.get("name"));
        assertEquals("描述", payload.get("description"));
        assertEquals(Boolean.TRUE, payload.get("archived"));
        assertEquals("2026-01-01T00:00:00Z", payload.get("created_at"));
        assertEquals("2026-01-02T00:00:00Z", payload.get("updated_at"));
        assertEquals("t", payload.get("user_team_id"));
        assertEquals("u", payload.get("user_id"));
        assertEquals("张三", payload.get("user_name"));
    }
}
