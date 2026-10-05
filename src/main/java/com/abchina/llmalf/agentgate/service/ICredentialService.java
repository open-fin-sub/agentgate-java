package com.abchina.llmalf.agentgate.service;

import com.abchina.llmalf.agentgate.service.vo.ApiKeyVO;

import java.util.List;

/**
 * API Key 管理服务.
 *
 * <p>对齐 Python application/credential_management.py::ApiKeyManagement。</p>
 */
public interface ICredentialService {

    /**
     * 是否可用(未配置主密钥时为 false,对应 Python api_keys=None).
     *
     * @return 可用为 true
     */
    boolean isAvailable();

    /**
     * 创建并加密保存 API Key,返回安全元数据.
     *
     * @param name 名称
     * @param providerId 提供方 id
     * @param scope 范围 wire 值
     * @param plaintext 明文密钥
     * @return 元数据视图
     */
    ApiKeyVO createApiKey(String name, String providerId, String scope, String plaintext);

    /**
     * 查询单个元数据.
     *
     * @param apiKeyId Key id
     * @return 元数据视图(不存在抛 404)
     */
    ApiKeyVO getApiKey(String apiKeyId);

    /**
     * 列出全部元数据(created_at 升序).
     *
     * @return 元数据视图列表
     */
    List<ApiKeyVO> listApiKeys();

    /**
     * 删除密钥(不存在抛 404).
     *
     * @param apiKeyId Key id
     */
    void deleteApiKey(String apiKeyId);
}
