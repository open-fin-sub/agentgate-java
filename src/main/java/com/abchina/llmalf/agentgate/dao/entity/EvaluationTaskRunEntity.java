package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 评测任务-Run 关联(agentgate_evaluation_task_runs).
 *
 * <p>纯行映射(薄列 + payload),对照 Python storage/mysql_schema.py;
 * agentgate_* 表无逻辑删除,不使用 @TableLogic。复合主键表:不标注 @TableId,DAO 走全 XML(不继承 BaseMapper)。</p>
 */
@Data
@TableName("agentgate_evaluation_task_runs")
public class EvaluationTaskRunEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Run 摘要(复合主键) */
    private byte[] runKey;

    /** Run id */
    private String runId;

    /** 任务摘要(复合主键) */
    private byte[] taskKey;

    /** 任务 id */
    private String taskId;
}
