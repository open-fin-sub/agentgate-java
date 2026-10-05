package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评测任务(agentgate_evaluation_tasks).
 *
 * <p>纯行映射(薄列 + payload),对照 Python storage/mysql_schema.py;
 * agentgate_* 表无逻辑删除,不使用 @TableLogic。</p>
 */
@Data
@TableName("agentgate_evaluation_tasks")
public class EvaluationTaskEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(id 摘要) */
    @TableId
    private byte[] idKey;

    /** 任务 id */
    private String id;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 领域对象 canonical JSON */
    private String payload;
}
