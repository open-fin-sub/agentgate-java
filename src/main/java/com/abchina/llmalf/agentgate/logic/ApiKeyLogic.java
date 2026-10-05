package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.dao.ApiKeyDAO;
import com.abchina.llmalf.agentgate.dao.entity.ApiKeyEntity;
import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.IdentityDigest;
import com.abchina.llmalf.agentgate.domain.model.credential.ApiKeyMetadata;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * API Key 域存储编排.
 *
 * <p>对齐 Python storage/mysql.py 的 api_keys 系列:元数据与密文分列,
 * 保存总是插入;列表按创建时间升序。</p>
 */
@Component
public class ApiKeyLogic {

    private final ApiKeyDAO apiKeyDAO;

    public ApiKeyLogic(ApiKeyDAO apiKeyDAO) {
        this.apiKeyDAO = apiKeyDAO;
    }

    /**
     * 保存 API Key(元数据 + 密文,总是插入).
     *
     * @param metadata 元数据
     * @param encryptedApiKey 密文(非空白)
     */
    @Transactional
    public void saveApiKey(ApiKeyMetadata metadata, String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("encrypted API Key must be a nonblank string");
        }
        try {
            ApiKeyEntity entity = new ApiKeyEntity();
            entity.setIdKey(IdentityDigest.of(metadata.id()));
            entity.setId(metadata.id());
            entity.setScope(metadata.scope().wireValue());
            entity.setProviderId(metadata.providerId());
            entity.setCreatedAt(metadata.createdAt().toLocalDateTime());
            entity.setMetadataPayload(CanonicalJson.serialize(metadata.toPayload()));
            entity.setEncryptedApiKey(encryptedApiKey);
            apiKeyDAO.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "database uniqueness or reference constraint conflict");
        }
    }

    /**
     * 查询密钥元数据.
     *
     * @param apiKeyId Key id
     * @return 元数据(可空)
     */
    @Transactional
    public ApiKeyMetadata getApiKeyMetadata(String apiKeyId) {
        ApiKeyEntity entity = findByKey(apiKeyId);
        if (entity == null) {
            return null;
        }
        ApiKeyMetadata metadata = ApiKeyMetadata.fromPayload(
                StorageModels.parsePayload(entity.getMetadataPayload()));
        verifyIndexed(metadata, entity);
        return metadata;
    }

    /**
     * 列出全部密钥元数据(created_at 升序).
     *
     * @return 元数据列表
     */
    @Transactional
    public List<ApiKeyMetadata> listApiKeyMetadata() {
        List<ApiKeyMetadata> items = new ArrayList<>();
        for (ApiKeyEntity entity : apiKeyDAO.selectAll()) {
            items.add(ApiKeyMetadata.fromPayload(
                    StorageModels.parsePayload(entity.getMetadataPayload())));
        }
        return StorageOrdering.ordered(items, ApiKeyMetadata::createdAt, false,
                ApiKeyMetadata::id);
    }

    /**
     * 读取密文.
     *
     * @param apiKeyId Key id
     * @return 密文(可空)
     */
    @Transactional
    public String getEncryptedApiKey(String apiKeyId) {
        ApiKeyEntity entity = findByKey(apiKeyId);
        return entity == null ? null : entity.getEncryptedApiKey();
    }

    /**
     * 删除密钥.
     *
     * @param apiKeyId Key id
     */
    @Transactional
    public void deleteApiKey(String apiKeyId) {
        ApiKeyEntity entity = findByKey(apiKeyId);
        if (entity == null) {
            throw new IllegalArgumentException("unknown API Key");
        }
        apiKeyDAO.deleteByKey(IdentityDigest.of(apiKeyId));
    }

    private ApiKeyEntity findByKey(String apiKeyId) {
        ApiKeyEntity entity = apiKeyDAO.selectByKey(IdentityDigest.of(apiKeyId));
        if (entity == null) {
            return null;
        }
        if (!entity.getId().equals(apiKeyId)) {
            throw new IllegalArgumentException("database identity digest collision");
        }
        return entity;
    }

    private static void verifyIndexed(ApiKeyMetadata metadata, ApiKeyEntity entity) {
        if (!java.util.Arrays.equals(IdentityDigest.of(metadata.id()), entity.getIdKey())
                || !metadata.id().equals(entity.getId())
                || !metadata.scope().wireValue().equals(entity.getScope())
                || !metadata.providerId().equals(entity.getProviderId())
                || !metadata.createdAt().toLocalDateTime().equals(entity.getCreatedAt())) {
            throw new IllegalArgumentException("stored indexed columns do not match payload");
        }
    }
}
