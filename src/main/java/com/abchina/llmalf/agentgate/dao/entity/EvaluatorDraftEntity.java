package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评测器配置草稿(agentgate_evaluator_drafts).
 *
 * <p>纯行映射(薄列 + payload),对照 Python storage/mysql_schema.py;
 * agentgate_* 表无逻辑删除,不使用 @TableLogic。</p>
 */
@Data
@TableName("agentgate_evaluator_drafts")
public class EvaluatorDraftEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(id 摘要) */
    @TableId
    private byte[] idKey;

    /** 草稿 id */
    private String id;

    /** 评测器摘要 */
    private byte[] evaluatorKey;

    /** 评测器 id */
    private String evaluatorId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 团队摘要 */
    private byte[] userTeamKey;

    /** 团队 id */
    private String userTeamId;

    /** 用户 id */
    private String userId;

    /** 用户名 */
    private String userName;

    /** 领域对象 canonical JSON */
    private String payload;
}
