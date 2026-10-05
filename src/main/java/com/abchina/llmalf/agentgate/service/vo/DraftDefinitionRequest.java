package com.abchina.llmalf.agentgate.service.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 评测器草稿定义请求(kind/dimension/metric/severity/implementation/config/children/combination).
 */
@Data
public class DraftDefinitionRequest {

    /** 执行方式(默认 rule) */
    private String kind;

    /** 维度 */
    private String dimension;

    /** 指标 */
    private String metric;

    /** 严重级(默认 standard) */
    private String severity;

    /** 实现 id */
    private String implementationId;

    /** 实现版本(默认 1) */
    private String implementationVersion;

    /** 配置(默认空) */
    private Map<String, Object> config;

    /** 子引用(默认空) */
    private List<Map<String, Object>> children;

    /** 组合策略(可空) */
    private String combination;
}
