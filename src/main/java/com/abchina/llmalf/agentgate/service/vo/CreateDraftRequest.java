package com.abchina.llmalf.agentgate.service.vo;

import lombok.Data;

/**
 * 草稿创建请求.
 */
@Data
public class CreateDraftRequest {

    /** 基线版本(可空,默认最新发布版) */
    private Integer basedOnVersion;
}
