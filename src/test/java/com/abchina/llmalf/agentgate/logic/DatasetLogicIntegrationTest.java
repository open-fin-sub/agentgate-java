package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.domain.model.dataset.Dataset;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersionStatus;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.cases.CaseTurn;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DatasetLogic 共库集成测试(随机 id + 事务回滚).
 *
 * <p>覆盖 save/get/list/版本规则/草稿删除与替换的正反路径,
 * 消息文本与 Python storage/mysql.py 逐字对照。</p>
 */
@SpringBootTest
@Transactional
class DatasetLogicIntegrationTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");

    @Autowired
    private DatasetLogic datasetLogic;

    private String newId() {
        return "it-ds-" + UUID.randomUUID();
    }

    private static Case caseOf(String id) {
        Map<String, Object> input = new HashMap<>();
        input.put("q", id);
        return Case.of(id, "用例-" + id,
                Collections.singletonList(CaseTurn.of(id + "-t1", input)));
    }

    private Dataset datasetOf(String id, OffsetDateTime created, OffsetDateTime updated) {
        return Dataset.of(id, "数据集-" + id, "描述", false, created, updated, "it-team", "u", "测试");
    }

    private DatasetVersion draftOf(String id, String datasetId, OffsetDateTime created,
            OffsetDateTime updated) {
        return DatasetVersion.of(id, datasetId, "", "", null, DatasetVersionStatus.DRAFT,
                null, null, "", created, updated, null, "", "it-team", "u", "测试");
    }

    private DatasetVersion draftWithCasesOf(String id, String datasetId, OffsetDateTime created,
            OffsetDateTime updated) {
        return DatasetVersion.of(id, datasetId, "", "", null, DatasetVersionStatus.DRAFT,
                null, Collections.singletonList(caseOf("c1")), "", created, updated, null,
                "", "it-team", "u", "测试");
    }

    private DatasetVersion publishedOf(String id, String datasetId, int version,
            OffsetDateTime created, OffsetDateTime published) {
        return DatasetVersion.of(id, datasetId, "", "", version, DatasetVersionStatus.PUBLISHED,
                null, Collections.singletonList(caseOf("c1")), "", created, published, published,
                "", "it-team", "u", "测试");
    }

    @Test
    void saveInsertsThenUpdatesWithGuards() {
        String id = newId();
        datasetLogic.saveDataset(datasetOf(id, T0, T0));
        assertEquals("数据集-" + id, datasetLogic.getDataset(id, "it-team").name());

        datasetLogic.saveDataset(datasetOf(id, T0, T1));
        assertEquals(T1, datasetLogic.getDataset(id, "it-team").updatedAt());

        assertEquals("Dataset created_at is immutable",
                assertThrows(IllegalArgumentException.class, () -> datasetLogic.saveDataset(
                        datasetOf(id, T1, T1))).getMessage());
        assertEquals("cannot save a stale Dataset",
                assertThrows(IllegalArgumentException.class, () -> datasetLogic.saveDataset(
                        datasetOf(id, T0, T0))).getMessage());

        assertNull(datasetLogic.getDataset(id, "other-team"));
        assertNotNull(datasetLogic.getDataset(id, "it-team"));
    }

    @Test
    void listDatasetsOrdersByUpdatedAtDescending() {
        String older = newId();
        String newer = newId();
        datasetLogic.saveDataset(datasetOf(older, T0, T0));
        datasetLogic.saveDataset(datasetOf(newer, T0, T1));
        List<Dataset> items = datasetLogic.listDatasets(false, "it-team");
        assertTrue(items.stream().anyMatch(d -> d.id().equals(older)));
        int olderIndex = -1;
        int newerIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(older)) {
                olderIndex = i;
            }
            if (items.get(i).id().equals(newer)) {
                newerIndex = i;
            }
        }
        assertTrue(newerIndex < olderIndex, "newer dataset must sort first");
    }

    @Test
    void saveDatasetVersionEnforcesRules() {
        String datasetId = newId();
        String draftId = newId();

        assertEquals("unknown Dataset",
                assertThrows(IllegalArgumentException.class, () -> datasetLogic.saveDatasetVersion(
                        draftOf(draftId, datasetId, T0, T0))).getMessage());

        datasetLogic.saveDataset(datasetOf(datasetId, T0, T0));
        datasetLogic.saveDatasetVersion(draftOf(draftId, datasetId, T0, T0));
        assertEquals(DatasetVersionStatus.DRAFT,
                datasetLogic.getDatasetDraft(datasetId, "it-team").status());

        datasetLogic.saveDatasetVersion(draftOf(draftId, datasetId, T0, T1));
        assertEquals(T1, datasetLogic.getDatasetDraft(datasetId, "it-team").updatedAt());

        assertEquals("DatasetVersion identity and created_at are immutable",
                assertThrows(IllegalArgumentException.class, () -> datasetLogic.saveDatasetVersion(
                        draftOf(draftId, datasetId, T1, T1))).getMessage());
        assertEquals("cannot save a stale DatasetVersion draft",
                assertThrows(IllegalArgumentException.class, () -> datasetLogic.saveDatasetVersion(
                        draftOf(draftId, datasetId, T0, T0))).getMessage());
        assertEquals("draft DatasetVersion cannot be published through save",
                assertThrows(IllegalArgumentException.class, () -> datasetLogic.saveDatasetVersion(
                        publishedOf(draftId, datasetId, 1, T0, T1))).getMessage());

        String publishedId = newId();
        datasetLogic.saveDatasetVersion(publishedOf(publishedId, datasetId, 1, T0, T1));
        DatasetVersion stored = datasetLogic.getPublishedDatasetVersion(datasetId, 1, "it-team");
        assertEquals(publishedId, stored.id());
        assertEquals("published DatasetVersion is immutable",
                assertThrows(IllegalArgumentException.class, () -> datasetLogic.saveDatasetVersion(
                        publishedOf(publishedId, datasetId, 1, T0, T1.plusDays(1))))
                        .getMessage());
    }

    @Test
    void versionListingOrdersDraftFirstThenVersionDescending() {
        String datasetId = newId();
        datasetLogic.saveDataset(datasetOf(datasetId, T0, T0));
        datasetLogic.saveDatasetVersion(publishedOf(newId(), datasetId, 1, T0, T1));
        datasetLogic.saveDatasetVersion(publishedOf(newId(), datasetId, 3, T0, T1));
        datasetLogic.saveDatasetVersion(publishedOf(newId(), datasetId, 2, T0, T1));
        datasetLogic.saveDatasetVersion(draftOf(newId(), datasetId, T0, T1));

        List<DatasetVersion> all = datasetLogic.listDatasetVersions(datasetId, true, "it-team");
        assertEquals(DatasetVersionStatus.DRAFT, all.get(0).status());
        assertEquals(Integer.valueOf(3), all.get(1).version());
        assertEquals(Integer.valueOf(2), all.get(2).version());
        assertEquals(Integer.valueOf(1), all.get(3).version());

        assertEquals(Integer.valueOf(3),
                datasetLogic.getLatestPublishedDatasetVersion(datasetId, "it-team").version());
        List<DatasetVersion> publishedOnly = datasetLogic.listDatasetVersions(datasetId, false,
                "it-team");
        assertEquals(3, publishedOnly.size());
    }

    @Test
    void deleteDatasetDraftRequiresExactIdentity() {
        String datasetId = newId();
        String draftId = newId();
        datasetLogic.saveDataset(datasetOf(datasetId, T0, T0));
        datasetLogic.saveDatasetVersion(draftOf(draftId, datasetId, T0, T0));

        assertEquals("expected Dataset draft does not exist",
                assertThrows(IllegalArgumentException.class, () -> datasetLogic.deleteDatasetDraft(
                        datasetId, "missing-draft", "it-team")).getMessage());
        assertEquals("expected Dataset draft does not exist",
                assertThrows(IllegalArgumentException.class, () -> datasetLogic.deleteDatasetDraft(
                        datasetId, draftId, "other-team")).getMessage());

        datasetLogic.deleteDatasetDraft(datasetId, draftId, "it-team");
        assertNull(datasetLogic.getDatasetDraft(datasetId, "it-team"));
    }

    @Test
    void replaceDatasetDraftPublishesAndRemovesDraft() {
        String datasetId = newId();
        String draftId = newId();
        datasetLogic.saveDataset(datasetOf(datasetId, T0, T0));
        DatasetVersion draft = draftWithCasesOf(draftId, datasetId, T0, T0);
        datasetLogic.saveDatasetVersion(draft);

        DatasetVersion published = DatasetVersion.of(newId(), datasetId, "", "", 1,
                DatasetVersionStatus.PUBLISHED, null, Collections.singletonList(caseOf("c1")),
                "", draft.createdAt(), draft.updatedAt(), draft.updatedAt(), "",
                "it-team", "u", "测试");

        assertEquals("expected Dataset draft does not exist",
                assertThrows(IllegalArgumentException.class, () -> datasetLogic.replaceDatasetDraft(
                        "missing-draft", published)).getMessage());

        datasetLogic.replaceDatasetDraft(draftId, published);
        assertNull(datasetLogic.getDatasetDraft(datasetId, "it-team"));
        assertEquals(published.id(),
                datasetLogic.getPublishedDatasetVersion(datasetId, 1, "it-team").id());

        DatasetVersion stale = draftWithCasesOf(newId(), datasetId, T0, T1);
        datasetLogic.saveDatasetVersion(stale);
        OffsetDateTime middle = T0.plusHours(12);
        DatasetVersion tooNew = DatasetVersion.of(newId(), datasetId, "", "", 2,
                DatasetVersionStatus.PUBLISHED, null, Collections.singletonList(caseOf("c1")),
                "", T0, middle, middle, "", "it-team", "u", "测试");
        assertEquals("cannot replace a newer DatasetVersion draft",
                assertThrows(IllegalArgumentException.class, () -> datasetLogic.replaceDatasetDraft(
                        stale.id(), tooNew)).getMessage());
        assertEquals("replacement must be a publication with a new identity",
                assertThrows(IllegalArgumentException.class, () -> datasetLogic.replaceDatasetDraft(
                        stale.id(), draftOf(stale.id(), datasetId, T0, T0))).getMessage());
    }
}
