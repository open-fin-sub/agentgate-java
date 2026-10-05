package com.abchina.llmalf.agentgate.service.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 数据集复制请求.
 */
@Data
public class CopyDatasetRequest {

    /** 新数据集名称 */
    @NotNull(message = "name 不能为空")
    private String name;

    /** 源版本号(可空,默认最新发布版) */
    private Integer sourceVersion;
}
