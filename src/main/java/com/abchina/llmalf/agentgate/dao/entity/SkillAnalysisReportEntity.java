package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 技能分析报告(仅行映射).
 *
 * <p>对照 Python storage/mysql_schema.py;domain 模型按裁决暂不实现,
 * 本实体仅为 EvaluationTask 的 static report 存在性查询服务。</p>
 */
@Data
@TableName("agentgate_skill_analysis_reports")
public class SkillAnalysisReportEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(id 摘要) */
    @TableId
    private byte[] idKey;

    /** 报告 id */
    private String id;

    /** 目标描述符摘要 */
    private String targetDescriptorSha256;

    /** 内容摘要 */
    private String contentSha256;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 领域对象 canonical JSON */
    private String payload;
}
