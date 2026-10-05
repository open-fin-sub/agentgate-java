package com.abchina.llmalf.agentgate.domain.model.dataset;

import com.abchina.llmalf.agentgate.domain.ContentSha256;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.cases.CaseTurn;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DatasetVersion 构造与解析行为测试(golden 未覆盖部分).
 */
class DatasetVersionTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");

    private static Case caseOf(String id) {
        Map<String, Object> input = new HashMap<>();
        input.put("q", id);
        return Case.of(id, "用例-" + id, Collections.singletonList(CaseTurn.of(id + "-t1", input)));
    }

    @Test
    void convenienceFactoryCreatesDraftWithDefaults() {
        DatasetVersion version = DatasetVersion.of("d1", Collections.singletonList(caseOf("c1")));
        assertEquals(DatasetVersionStatus.DRAFT, version.status());
        assertEquals(null, version.version());
        assertEquals(null, version.publishedAt());
        assertEquals("", version.datasetName());
        assertTrue(version.id().trim().length() > 0, "generated id must not be blank");
        assertNotNull(version.createdAt());
        assertNotNull(version.updatedAt());
        assertTrue(!version.contentSha256().isEmpty(), "hash must be auto-computed");
    }

    @Test
    void draftAllowsEmptyCasesAndAutoComputesHash() {
        DatasetVersion empty = DatasetVersion.of("v1", "d1", "", "", null,
                DatasetVersionStatus.DRAFT, null, null, "", T0, T0, null, "", "", "", "");
        assertTrue(empty.cases().isEmpty());
        assertTrue(empty.contentSha256().matches("[0-9a-f]{64}"));

        DatasetVersion withCases = DatasetVersion.of("v1", "d1", "", "", null,
                DatasetVersionStatus.DRAFT, null,
                Collections.singletonList(caseOf("c1")), "", T0, T0, null, "", "", "", "");
        assertNotEquals(empty.contentSha256(), withCases.contentSha256(),
                "hash must depend on cases");
    }

    @Test
    void hashIsTimeAndMetadataIndependent() {
        String hashFirst = DatasetVersion.of("v1", "d1", "", "", null,
                DatasetVersionStatus.DRAFT, null,
                Collections.singletonList(caseOf("c1")), "备注", T0, T0, null, "", "", "", "")
                .contentSha256();
        String hashLater = DatasetVersion.of("v2", "d1", "名称快照", "描述快照", null,
                DatasetVersionStatus.DRAFT, null,
                Collections.singletonList(caseOf("c1")), "备注", T1, T1, null, "", "t", "u", "名")
                .contentSha256();
        assertEquals(hashFirst, hashLater,
                "hash must depend only on dataset_id/cases/notes");
    }

    @Test
    void explicitCorrectHashIsAccepted() {
        DatasetVersion base = DatasetVersion.of("v1", "d1", "", "", null,
                DatasetVersionStatus.DRAFT, null,
                Collections.singletonList(caseOf("c1")), "备注", T0, T0, null, "", "", "", "");
        DatasetVersion verified = DatasetVersion.of("v1", "d1", "", "", null,
                DatasetVersionStatus.DRAFT, null,
                Collections.singletonList(caseOf("c1")), "备注", T0, T0, null,
                base.contentSha256(), "", "", "");
        assertEquals(base.contentSha256(), verified.contentSha256());
    }

    @Test
    void hashMismatchIsRejected() {
        assertEquals("DatasetVersion content hash mismatch",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.of(
                        "v1", "d1", "", "", null, DatasetVersionStatus.DRAFT, null, null, "",
                        T0, T0, null, "0000000000000000000000000000000000000000000000000000000000000000",
                        "", "", "")).getMessage());
    }

    @Test
    void publishedVersionRequiresFullChain() {
        DatasetVersion published = DatasetVersion.of("v1", "d1", "", "", 2,
                DatasetVersionStatus.PUBLISHED, 1,
                Collections.singletonList(caseOf("c1")), "", T0, T1, T1, "", "", "", "");
        assertEquals(Integer.valueOf(2), published.version());
        assertEquals(Integer.valueOf(1), published.basedOnVersion());
        assertEquals(T1, published.publishedAt());
    }

    @Test
    void versionBoundsValidation() {
        assertEquals("Input should be greater than or equal to 1",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.of(
                        "v1", "d1", "", "", 0, DatasetVersionStatus.PUBLISHED, null, null, "",
                        T0, T0, null, "", "", "", "")).getMessage());
        assertEquals("Input should be greater than or equal to 1",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.of(
                        "v1", "d1", "", "", 1, DatasetVersionStatus.PUBLISHED, 0, null, "",
                        T0, T0, null, "", "", "", "")).getMessage());
        assertEquals("DatasetVersion dataset_id must not be blank",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.of(
                        "v1", " ", "", "", null, DatasetVersionStatus.DRAFT, null, null, "",
                        T0, T0, null, "", "", "", "")).getMessage());
    }

    @Test
    void statusRulesValidation() {
        assertEquals("draft DatasetVersion cannot be numbered or published",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.of(
                        "v1", "d1", "", "", 1, DatasetVersionStatus.DRAFT, null, null, "",
                        T0, T0, null, "", "", "", "")).getMessage());
        assertEquals("published DatasetVersion requires version and published_at",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.of(
                        "v1", "d1", "", "", null, DatasetVersionStatus.PUBLISHED, null,
                        Collections.singletonList(caseOf("c1")), "", T0, T1, null, "", "", "", ""))
                        .getMessage());
        assertEquals("published DatasetVersion requires at least one Case",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.of(
                        "v1", "d1", "", "", 1, DatasetVersionStatus.PUBLISHED, null,
                        new ArrayList<Case>(), "", T0, T1, T1, "", "", "", "")).getMessage());
        assertEquals("published_at must not precede created_at",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.of(
                        "v1", "d1", "", "", 1, DatasetVersionStatus.PUBLISHED, null,
                        Collections.singletonList(caseOf("c1")), "", T0, T1,
                        T0.minusSeconds(1), "", "", "", "")).getMessage());
        assertEquals("published_at must not follow updated_at",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.of(
                        "v1", "d1", "", "", 1, DatasetVersionStatus.PUBLISHED, null,
                        Collections.singletonList(caseOf("c1")), "", T0, T1,
                        T1.plusSeconds(1), "", "", "", "")).getMessage());
        assertEquals("based_on_version must precede version",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.of(
                        "v1", "d1", "", "", 1, DatasetVersionStatus.PUBLISHED, 1,
                        Collections.singletonList(caseOf("c1")), "", T0, T1, T1, "", "", "", ""))
                        .getMessage());
        assertEquals("updated_at must not precede created_at",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.of(
                        "v1", "d1", "", "", null, DatasetVersionStatus.DRAFT, null, null, "",
                        T1, T0, null, "", "", "", "")).getMessage());
    }

    @Test
    void duplicateCaseIdsAreRejected() {
        assertEquals("Case ids must be unique within a DatasetVersion",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.of(
                        "v1", "d1", "", "", null, DatasetVersionStatus.DRAFT, null,
                        Arrays.asList(caseOf("c1"), caseOf("c1")), "", T0, T0, null, "", "", "", ""))
                        .getMessage());
    }

    @Test
    void casesListIsImmutable() {
        List<Case> source = new ArrayList<>(Collections.singletonList(caseOf("c1")));
        DatasetVersion version = DatasetVersion.of("v1", "d1", "", "", null,
                DatasetVersionStatus.DRAFT, null, source, "", T0, T0, null, "", "", "", "");
        assertThrows(UnsupportedOperationException.class, () -> version.cases().clear());
        source.clear();
        assertEquals(1, version.cases().size(), "of must copy the cases list");
    }

    @Test
    void hashMatchesDirectContentSha256Computation() {
        Case only = caseOf("c1");
        DatasetVersion version = DatasetVersion.of("v1", "d1", "", "", null,
                DatasetVersionStatus.DRAFT, null,
                Collections.singletonList(only), "备注", T0, T0, null, "", "", "", "");
        Map<String, Object> expectedInput = new HashMap<>();
        expectedInput.put("dataset_id", "d1");
        List<Object> casePayloads = new ArrayList<>();
        casePayloads.add(only.toPayload());
        expectedInput.put("cases", casePayloads);
        expectedInput.put("notes", "备注");
        assertEquals(ContentSha256.of(expectedInput), version.contentSha256());
    }

    @Test
    void fromPayloadAppliesDefaults() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("dataset_id", "d1");
        DatasetVersion version = DatasetVersion.fromPayload(payload);
        assertTrue(version.id().trim().length() > 0);
        assertEquals(DatasetVersionStatus.DRAFT, version.status());
        assertEquals(null, version.version());
        assertTrue(version.cases().isEmpty());
        assertEquals("", version.notes());
        assertNotNull(version.createdAt());
        assertTrue(version.contentSha256().matches("[0-9a-f]{64}"));
    }

    @Test
    void fromPayloadFloatVersionIsRejectedWithFractionalMessage() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("dataset_id", "d1");
        payload.put("version", 1.5);
        assertEquals("Input should be a valid integer, got a number with a fractional part",
                assertThrows(IllegalArgumentException.class, () -> DatasetVersion.fromPayload(payload))
                        .getMessage());
    }

    @Test
    void toPayloadContainsAllFieldsInOrder() {
        DatasetVersion version = DatasetVersion.of("v1", "d1", "名", "述", 2,
                DatasetVersionStatus.PUBLISHED, 1,
                Collections.singletonList(caseOf("c1")), "备注", T0, T1, T1, "", "t", "u", "n");
        Map<?, ?> payload = (Map<?, ?>) version.toPayload();
        assertEquals("v1", payload.get("id"));
        assertEquals("d1", payload.get("dataset_id"));
        assertEquals("名", payload.get("dataset_name"));
        assertEquals("述", payload.get("dataset_description"));
        assertEquals(2, payload.get("version"));
        assertEquals("published", payload.get("status"));
        assertEquals(1, payload.get("based_on_version"));
        assertEquals("备注", payload.get("notes"));
        assertEquals("2026-01-01T00:00:00Z", payload.get("created_at"));
        assertEquals("2026-01-02T00:00:00Z", payload.get("updated_at"));
        assertEquals("2026-01-02T00:00:00Z", payload.get("published_at"));
        assertEquals(version.contentSha256(), payload.get("content_sha256"));
        assertEquals("t", payload.get("user_team_id"));
        assertEquals(16, payload.size(), "payload must contain all 16 fields");
    }
}
