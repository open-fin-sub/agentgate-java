package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * entity 表名与主键注解映射测试.
 *
 * <p>对照 Python storage/mysql_schema.py 的表名与主键结构,防止拼写错误
 * (表名/主键注解错误只会在运行期暴露,此处编译后静态断言)。</p>
 */
class EntityMappingsTest {

    private static final Set<String> COMPOSITE_KEY_ENTITIES = new HashSet<>(Arrays.asList(
            "EvaluatorVersionEntity", "EvaluationTaskRunEntity"));

    @Test
    void tableNamesMatchSchema() {
        assertEquals("agentgate_datasets", tableOf(DatasetEntity.class));
        assertEquals("agentgate_dataset_versions", tableOf(DatasetVersionEntity.class));
        assertEquals("agentgate_evaluators", tableOf(EvaluatorEntity.class));
        assertEquals("agentgate_evaluator_drafts", tableOf(EvaluatorDraftEntity.class));
        assertEquals("agentgate_evaluator_versions", tableOf(EvaluatorVersionEntity.class));
        assertEquals("agentgate_runs", tableOf(RunEntity.class));
        assertEquals("agentgate_run_asset_refs", tableOf(RunAssetRefEntity.class));
        assertEquals("agentgate_traces", tableOf(TraceEntity.class));
        assertEquals("agentgate_results", tableOf(ResultEntity.class));
        assertEquals("agentgate_target_descriptors", tableOf(TargetDescriptorEntity.class));
        assertEquals("agentgate_evaluation_tasks", tableOf(EvaluationTaskEntity.class));
        assertEquals("agentgate_evaluation_task_runs", tableOf(EvaluationTaskRunEntity.class));
        assertEquals("agentgate_api_keys", tableOf(ApiKeyEntity.class));
    }

    @Test
    void singleKeyEntitiesDeclareExactlyOneTableId() {
        assertSingleTableId(DatasetEntity.class, "idKey");
        assertSingleTableId(DatasetVersionEntity.class, "idKey");
        assertSingleTableId(EvaluatorEntity.class, "idKey");
        assertSingleTableId(EvaluatorDraftEntity.class, "idKey");
        assertSingleTableId(RunEntity.class, "idKey");
        assertSingleTableId(RunAssetRefEntity.class, "referenceKey");
        assertSingleTableId(TraceEntity.class, "idKey");
        assertSingleTableId(ResultEntity.class, "idKey");
        assertSingleTableId(TargetDescriptorEntity.class, "contentSha256");
        assertSingleTableId(EvaluationTaskEntity.class, "idKey");
        assertSingleTableId(ApiKeyEntity.class, "idKey");
    }

    @Test
    void compositeKeyEntitiesDeclareNoTableId() {
        assertNoTableId(EvaluatorVersionEntity.class);
        assertNoTableId(EvaluationTaskRunEntity.class);
    }

    @Test
    void allEntitiesAreSerializableWithSerialVersionUid() {
        Class<?>[] entities = {DatasetEntity.class, DatasetVersionEntity.class,
                EvaluatorEntity.class, EvaluatorDraftEntity.class, EvaluatorVersionEntity.class,
                RunEntity.class, RunAssetRefEntity.class, TraceEntity.class, ResultEntity.class,
                TargetDescriptorEntity.class, EvaluationTaskEntity.class,
                EvaluationTaskRunEntity.class, ApiKeyEntity.class};
        for (Class<?> entity : entities) {
            assertTrue(java.io.Serializable.class.isAssignableFrom(entity),
                    entity.getSimpleName() + " must be Serializable");
            boolean hasSerialVersionUid = false;
            for (Field field : entity.getDeclaredFields()) {
                if (field.getName().equals("serialVersionUID")
                        && Modifier.isStatic(field.getModifiers())) {
                    hasSerialVersionUid = true;
                }
            }
            assertTrue(hasSerialVersionUid, entity.getSimpleName() + " must declare serialVersionUID");
        }
    }

    private static String tableOf(Class<?> entity) {
        TableName annotation = entity.getAnnotation(TableName.class);
        return annotation == null ? null : annotation.value();
    }

    private static void assertSingleTableId(Class<?> entity, String expectedField) {
        int count = 0;
        for (Field field : entity.getDeclaredFields()) {
            if (field.isAnnotationPresent(TableId.class)) {
                count++;
                assertEquals(expectedField, field.getName(),
                        entity.getSimpleName() + " @TableId field");
            }
        }
        assertEquals(1, count, entity.getSimpleName() + " must declare exactly one @TableId");
    }

    private static void assertNoTableId(Class<?> entity) {
        for (Field field : entity.getDeclaredFields()) {
            assertTrue(!field.isAnnotationPresent(TableId.class),
                    entity.getSimpleName() + " must not declare @TableId (composite key, "
                            + "DAO uses XML only)");
        }
        assertTrue(COMPOSITE_KEY_ENTITIES.contains(entity.getSimpleName()));
    }
}
