package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据集版本快照(agentgate_dataset_versions).
 *
 * <p>纯行映射(薄列 + payload),对照 Python storage/mysql_schema.py;
 * agentgate_* 表无逻辑删除,不使用 @TableLogic。</p>
 */
@Data
@TableName("agentgate_dataset_versions")
public class DatasetVersionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(id 摘要) */
    @TableId
    private byte[] idKey;

    /** 版本 id */
    private String id;

    /** 数据集摘要 */
    private byte[] datasetKey;

    /** 数据集 id */
    private String datasetId;

    /** 版本号(草稿为 null) */
    private Long version;

    /** 状态 wire 值 */
    private String status;

    /** 草稿槽位(发布为 null) */
    private Integer draftSlot;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 内容摘要 */
    private String contentSha256;

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
