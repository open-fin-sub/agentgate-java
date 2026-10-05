package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.common.ResponseBase;
import com.abchina.llmalf.agentgate.service.ICredentialService;
import com.abchina.llmalf.agentgate.service.vo.ApiKeyVO;
import com.abchina.llmalf.agentgate.service.vo.CreateApiKeyRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * API Key 管理端点.
 *
 * <p>对齐 Python server/routes/credentials.py:创建(201)/列表/查询(404)/
 * 删除(204);未配置主密钥时 503。密钥永不回显。</p>
 */
@RestController
@RequestMapping("/api/api-keys")
public class CredentialController {

    private final ICredentialService credentialService;

    public CredentialController(ICredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /**
     * 创建 API Key.
     *
     * @param request 创建请求
     * @return 安全元数据
     */
    @PostMapping
    public ResponseEntity<ResponseBase<ApiKeyVO>> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request) {
        ApiKeyVO vo = credentialService.createApiKey(request.getName(),
                request.getProviderId(), request.getScope(), request.getApiKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseBase.success(vo));
    }

    /**
     * 列出全部 API Key 元数据.
     *
     * @return 元数据列表
     */
    @GetMapping
    public ResponseBase<List<ApiKeyVO>> listApiKeys() {
        return ResponseBase.success(credentialService.listApiKeys());
    }

    /**
     * 查询单个 API Key 元数据.
     *
     * @param apiKeyId Key id
     * @return 元数据
     */
    @GetMapping("/{apiKeyId}")
    public ResponseBase<ApiKeyVO> getApiKey(@PathVariable("apiKeyId") String apiKeyId) {
        return ResponseBase.success(credentialService.getApiKey(apiKeyId));
    }

    /**
     * 删除 API Key.
     *
     * @param apiKeyId Key id
     * @return 204 无内容
     */
    @DeleteMapping("/{apiKeyId}")
    public ResponseEntity<Void> deleteApiKey(@PathVariable("apiKeyId") String apiKeyId) {
        credentialService.deleteApiKey(apiKeyId);
        return ResponseEntity.noContent().build();
    }
}
