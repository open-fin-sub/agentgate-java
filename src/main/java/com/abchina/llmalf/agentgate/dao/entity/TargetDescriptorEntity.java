package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 目标描述符(agentgate_target_descriptors).
 *
 * <p>纯行映射(薄列 + payload),对照 Python storage/mysql_schema.py;
 * agentgate_* 表无逻辑删除,不使用 @TableLogic。</p>
 */
@Data
@TableName("agentgate_target_descriptors")
public class TargetDescriptorEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(内容摘要) */
    @TableId
    private String contentSha256;

    /** 目标身份摘要 */
    private byte[] targetRefKey;

    /** 来源 id */
    private String sourceId;

    /** 类型 wire 值 */
    private String targetType;

    /** 外部目标 id */
    private String externalTargetId;

    /** 外部版本 id */
    private String externalVersionId;

    /** 抓取时间 */
    private LocalDateTime fetchedAt;

    /** 领域对象 canonical JSON */
    private String payload;
}
