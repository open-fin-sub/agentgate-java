package com.abchina.llmalf.agentgate.service.vo;

import com.abchina.llmalf.agentgate.domain.model.credential.ApiKeyMetadata;
import lombok.Data;

/**
 * API Key 元数据视图.
 *
 * <p>对齐 Python ApiKeyMetadata 的 model_dump 输出:
 * id/name/provider_id/scope/created_at/updated_at(isoformat Z)。</p>
 */
@Data
public class ApiKeyVO {

    /** Key id */
    private String id;

    /** 名称 */
    private String name;

    /** 提供方 id */
    private String providerId;

    /** 范围 wire 值 */
    private String scope;

    /** 创建时间(isoformat Z) */
    private String createdAt;

    /** 更新时间(isoformat Z) */
    private String updatedAt;

    /**
     * 由领域对象组装视图.
     *
     * @param metadata 元数据
     * @return 视图
     */
    public static ApiKeyVO from(ApiKeyMetadata metadata) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.setId(metadata.id());
        vo.setName(metadata.name());
        vo.setProviderId(metadata.providerId());
        vo.setScope(metadata.scope().wireValue());
        vo.setCreatedAt(com.abchina.llmalf.agentgate.domain.DomainValidations
                .isoFormat(metadata.createdAt()));
        vo.setUpdatedAt(com.abchina.llmalf.agentgate.domain.DomainValidations
                .isoFormat(metadata.updatedAt()));
        return vo;
    }
}
