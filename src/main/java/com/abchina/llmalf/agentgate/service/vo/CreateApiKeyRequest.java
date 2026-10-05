package com.abchina.llmalf.agentgate.service.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * API Key 创建请求.
 *
 * <p>对齐 Python CreateApiKeyRequest(name/provider_id/scope/api_key,
 * extra=forbid);仅做存在性校验,空白值放行到领域层
 * (消息与 pydantic+domain 链对齐);api_key 仅入参不回显。</p>
 */
@Data
public class CreateApiKeyRequest {

    /** 名称 */
    @NotNull(message = "name 不能为空")
    private String name;

    /** 提供方 id */
    @NotNull(message = "provider_id 不能为空")
    private String providerId;

    /** 范围(shared/private) */
    @NotNull(message = "scope 不能为空")
    private String scope;

    /** 明文密钥(仅写入,不回显) */
    @NotNull(message = "api_key 不能为空")
    private String apiKey;
}
