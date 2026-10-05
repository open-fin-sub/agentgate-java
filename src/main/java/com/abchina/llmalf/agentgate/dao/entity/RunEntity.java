package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评测 Run(agentgate_runs).
 *
 * <p>纯行映射(薄列 + payload),对照 Python storage/mysql_schema.py;
 * agentgate_* 表无逻辑删除,不使用 @TableLogic。</p>
 */
@Data
@TableName("agentgate_runs")
public class RunEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(id 摘要) */
    @TableId
    private byte[] idKey;

    /** Run id */
    private String id;

    /** 状态 wire 值 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 计划释放时间 */
    private LocalDateTime scheduledFor;

    /** 团队摘要 */
    private byte[] userTeamKey;

    /** 团队 id */
    private String userTeamId;

    /** 用户 id */
    private String userId;

    /** 用户名 */
    private String userName;

    /** API Key */
    private String apiKey;

    /** 领域对象 canonical JSON */
    private String payload;
}
