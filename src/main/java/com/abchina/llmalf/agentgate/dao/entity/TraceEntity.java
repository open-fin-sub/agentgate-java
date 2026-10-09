package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 执行轨迹(agentgate_traces).
 *
 * <p>纯行映射(薄列 + payload),对照 Python storage/mysql_schema.py;
 * agentgate_* 表无逻辑删除,不使用 @TableLogic。</p>
 */
@Data
@TableName("agentgate_traces")
public class TraceEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(trace_id 摘要) */
    @TableId
    private byte[] idKey;

    /** Trace id */
    private String id;

    /** Run 摘要 */
    private byte[] runKey;

    /** Run id */
    private String runId;

    /** 用例摘要 */
    private byte[] caseKey;

    /** 用例 id */
    private String caseId;

    /** 领域对象 canonical JSON */
    private String payload;
}
