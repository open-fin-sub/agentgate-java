package com.abchina.llmalf.agentgate.service.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 数据集创建请求.
 */
@Data
public class CreateDatasetRequest {

    /** 名称 */
    @NotNull(message = "name 不能为空")
    private String name;

    /** 描述 */
    private String description;
}
