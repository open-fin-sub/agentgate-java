package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.domain.model.credential.ApiKeyMetadata;
import com.abchina.llmalf.agentgate.domain.model.credential.ApiKeyScope;
import com.abchina.llmalf.agentgate.logic.ApiKeyEncryptor;
import com.abchina.llmalf.agentgate.logic.ApiKeyLogic;
import com.abchina.llmalf.agentgate.service.ICredentialService;
import com.abchina.llmalf.agentgate.service.vo.ApiKeyVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * API Key 管理服务实现.
 *
 * <p>对齐 Python ApiKeyManagement:加密存储/安全元数据/404 语义;
 * 未装配加密器时全部入口 503。</p>
 */
@Service
public class CredentialServiceImpl implements ICredentialService {

    private static final String UNAVAILABLE = "API Key management is unavailable";

    private final ApiKeyLogic apiKeyLogic;
    private final ObjectProvider<ApiKeyEncryptor> encryptorProvider;

    public CredentialServiceImpl(ApiKeyLogic apiKeyLogic,
            ObjectProvider<ApiKeyEncryptor> encryptorProvider) {
        this.apiKeyLogic = apiKeyLogic;
        this.encryptorProvider = encryptorProvider;
    }

    @Override
    public boolean isAvailable() {
        return encryptorProvider.getIfAvailable() != null;
    }

    @Override
    public ApiKeyVO createApiKey(String name, String providerId, String scope, String plaintext) {
        ApiKeyEncryptor encryptor = requireEncryptor();
        try {
            ApiKeyMetadata metadata = ApiKeyMetadata.of(UUID.randomUUID().toString(),
                    name.trim(), providerId.trim(), ApiKeyScope.fromWireValue(scope), null, null);
            String encrypted = encryptor.encrypt(plaintext);
            apiKeyLogic.saveApiKey(metadata, encrypted);
            return ApiKeyVO.from(metadata);
        } catch (IllegalArgumentException error) {
            throw unprocessable(error);
        }
    }

    @Override
    public ApiKeyVO getApiKey(String apiKeyId) {
        requireEncryptor();
        ApiKeyMetadata metadata = apiKeyLogic.getApiKeyMetadata(apiKeyId);
        if (metadata == null) {
            throw notFound(apiKeyId);
        }
        return ApiKeyVO.from(metadata);
    }

    @Override
    public List<ApiKeyVO> listApiKeys() {
        requireEncryptor();
        List<ApiKeyVO> result = new ArrayList<>();
        for (ApiKeyMetadata metadata : apiKeyLogic.listApiKeyMetadata()) {
            result.add(ApiKeyVO.from(metadata));
        }
        return result;
    }

    @Override
    public void deleteApiKey(String apiKeyId) {
        getApiKey(apiKeyId);
        try {
            apiKeyLogic.deleteApiKey(apiKeyId);
        } catch (IllegalArgumentException error) {
            throw notFound(apiKeyId);
        }
    }

    private ApiKeyEncryptor requireEncryptor() {
        ApiKeyEncryptor encryptor = encryptorProvider.getIfAvailable();
        if (encryptor == null) {
            throw new AgentException(503, UNAVAILABLE);
        }
        return encryptor;
    }

    private static AgentException notFound(String apiKeyId) {
        return new AgentException(404, "unknown API Key: " + apiKeyId);
    }

    private static AgentException unprocessable(IllegalArgumentException error) {
        return new AgentException(422, error.getMessage());
    }
}
