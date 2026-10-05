package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.dao.TargetDescriptorDAO;
import com.abchina.llmalf.agentgate.dao.entity.TargetDescriptorEntity;
import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.IdentityDigest;
import com.abchina.llmalf.agentgate.domain.model.target.TargetDescriptor;
import com.abchina.llmalf.agentgate.domain.model.target.TargetRef;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Target 域存储编排.
 *
 * <p>对齐 Python storage/mysql.py 的 TargetDescriptor 系列:
 * 内容寻址(排除 fetched_at 比较)、引用匹配守护与双键排序。</p>
 */
@Component
public class TargetLogic {

    private final TargetDescriptorDAO targetDescriptorDAO;

    public TargetLogic(TargetDescriptorDAO targetDescriptorDAO) {
        this.targetDescriptorDAO = targetDescriptorDAO;
    }

    /**
     * 保存目标描述符(同 hash 内容冲突拒绝).
     *
     * @param descriptor 描述符
     */
    @Transactional
    public void saveTargetDescriptor(TargetDescriptor descriptor) {
        try {
            TargetDescriptorEntity storedEntity = targetDescriptorDAO.selectByIdForUpdate(
                    descriptor.contentSha256());
            if (storedEntity != null) {
                TargetDescriptor stored = toDescriptor(storedEntity);
                if (!sameExceptFetchedAt(stored, descriptor)) {
                    throw new IllegalArgumentException("TargetDescriptor content hash collision");
                }
            } else {
                targetDescriptorDAO.insert(toEntity(descriptor));
            }
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 按内容摘要查询描述符.
     *
     * @param contentSha256 内容摘要
     * @return 描述符(可空)
     */
    @Transactional
    public TargetDescriptor getTargetDescriptor(String contentSha256) {
        TargetDescriptorEntity entity = targetDescriptorDAO.selectById(contentSha256);
        return entity == null ? null : toDescriptor(entity);
    }

    /**
     * 列出描述符(可按目标身份过滤;content_sha256 升序次键,fetched_at 降序主键).
     *
     * @param ref 目标身份(可空)
     * @return 描述符列表
     */
    @Transactional
    public List<TargetDescriptor> listTargetDescriptors(TargetRef ref) {
        List<TargetDescriptor> items = new ArrayList<>();
        if (ref != null) {
            byte[] refKey = IdentityDigest.of(ref.sourceId(), ref.targetType().wireValue(),
                    ref.externalTargetId(), ref.externalVersionId());
            for (TargetDescriptorEntity entity : targetDescriptorDAO.selectByTargetRefKey(refKey)) {
                items.add(toDescriptor(entity));
            }
            for (TargetDescriptor item : items) {
                if (!sameRef(item.ref(), ref)) {
                    throw new IllegalArgumentException("database identity digest collision");
                }
            }
        } else {
            for (TargetDescriptorEntity entity : targetDescriptorDAO.selectList(null)) {
                items.add(toDescriptor(entity));
            }
        }
        items.sort((left, right) -> left.contentSha256().compareTo(right.contentSha256()));
        items.sort((left, right) -> right.fetchedAt().compareTo(left.fetchedAt()));
        return items;
    }

    private static boolean sameRef(TargetRef left, TargetRef right) {
        return left.sourceId().equals(right.sourceId())
                && left.targetType() == right.targetType()
                && left.externalTargetId().equals(right.externalTargetId())
                && left.externalVersionId().equals(right.externalVersionId());
    }

    private static boolean sameExceptFetchedAt(TargetDescriptor left, TargetDescriptor right) {
        return StorageModels.samePayload(
                excludeFetchedAt(left.toPayload()), excludeFetchedAt(right.toPayload()));
    }

    private static Object excludeFetchedAt(Object payloadTree) {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>(
                (java.util.Map<String, Object>) payloadTree);
        payload.remove("fetched_at");
        return payload;
    }

    private static boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private TargetDescriptor toDescriptor(TargetDescriptorEntity entity) {
        TargetDescriptor descriptor = TargetDescriptor.fromPayload(
                StorageModels.parsePayload(entity.getPayload()));
        verifyIndexed(toEntity(descriptor), entity);
        return descriptor;
    }

    private TargetDescriptorEntity toEntity(TargetDescriptor descriptor) {
        TargetDescriptorEntity entity = new TargetDescriptorEntity();
        entity.setContentSha256(descriptor.contentSha256());
        entity.setTargetRefKey(IdentityDigest.of(descriptor.ref().sourceId(),
                descriptor.ref().targetType().wireValue(),
                descriptor.ref().externalTargetId(), descriptor.ref().externalVersionId()));
        entity.setSourceId(descriptor.ref().sourceId());
        entity.setTargetType(descriptor.ref().targetType().wireValue());
        entity.setExternalTargetId(descriptor.ref().externalTargetId());
        entity.setExternalVersionId(descriptor.ref().externalVersionId());
        entity.setFetchedAt(descriptor.fetchedAt().toLocalDateTime());
        entity.setPayload(CanonicalJson.serialize(descriptor.toPayload()));
        return entity;
    }

    private static void verifyIndexed(TargetDescriptorEntity rebuilt,
            TargetDescriptorEntity stored) {
        if (!equalsNullable(rebuilt.getContentSha256(), stored.getContentSha256())
                || !java.util.Arrays.equals(rebuilt.getTargetRefKey(), stored.getTargetRefKey())
                || !equalsNullable(rebuilt.getSourceId(), stored.getSourceId())
                || !equalsNullable(rebuilt.getTargetType(), stored.getTargetType())
                || !equalsNullable(rebuilt.getExternalTargetId(), stored.getExternalTargetId())
                || !equalsNullable(rebuilt.getExternalVersionId(), stored.getExternalVersionId())
                || !equalsNullable(rebuilt.getFetchedAt(), stored.getFetchedAt())) {
            throw new IllegalArgumentException("stored indexed columns do not match payload");
        }
    }
}
