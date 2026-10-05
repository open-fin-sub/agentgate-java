package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评测结论(agentgate_results).
 *
 * <p>纯行映射(薄列 + payload),对照 Python storage/mysql_schema.py;
 * agentgate_* 表无逻辑删除,不使用 @TableLogic。</p>
 */
@Data
@TableName("agentgate_results")
public class ResultEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(id 摘要) */
    @TableId
    private byte[] idKey;

    /** 结论 id */
    private String id;

    /** Run 摘要 */
    private byte[] runKey;

    /** Run id */
    private String runId;

    /** 用例摘要 */
    private byte[] caseKey;

    /** 用例 id */
    private String caseId;

    /** Trace 摘要 */
    private byte[] traceKey;

    /** Trace id */
    private String traceId;

    /** 评测器摘要 */
    private byte[] evaluatorKey;

    /** 评测器 id */
    private String evaluatorId;

    /** 领域对象 canonical JSON */
    private String payload;
}
