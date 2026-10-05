package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * API Key(agentgate_api_keys).
 *
 * <p>纯行映射(薄列 + payload),对照 Python storage/mysql_schema.py;
 * agentgate_* 表无逻辑删除,不使用 @TableLogic。</p>
 */
@Data
@TableName("agentgate_api_keys")
public class ApiKeyEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(id 摘要) */
    @TableId
    private byte[] idKey;

    /** Key id */
    private String id;

    /** 范围 wire 值 */
    private String scope;

    /** 提供方 id */
    private String providerId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 元数据 canonical JSON */
    private String metadataPayload;

    /** 密文( AES-256-GCM v1.{base64} ) */
    private String encryptedApiKey;
}
