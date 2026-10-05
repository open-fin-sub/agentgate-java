package com.abchina.llmalf.agentgate.service.vo;

import lombok.Data;

/**
 * 数据集更新请求(可选字段,null 表示不修改).
 */
@Data
public class UpdateDatasetRequest {

    /** 名称(可空) */
    private String name;

    /** 描述(可空) */
    private String description;

    /** 归档标记(可空) */
    private Boolean archived;
}
