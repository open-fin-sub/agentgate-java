package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * Run 资产引用(agentgate_run_asset_refs).
 *
 * <p>纯行映射(薄列 + payload),对照 Python storage/mysql_schema.py;
 * agentgate_* 表无逻辑删除,不使用 @TableLogic。</p>
 */
@Data
@TableName("agentgate_run_asset_refs")
public class RunAssetRefEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(引用摘要) */
    @TableId
    private byte[] referenceKey;

    /** Run 摘要 */
    private byte[] runKey;

    /** Run id */
    private String runId;

    /** 资产查询摘要 */
    private byte[] assetLookupKey;

    /** 资产类型 */
    private String assetKind;

    /** 来源 id */
    private String sourceId;

    /** 资产 id */
    private String assetId;

    /** 资产版本 */
    private String version;

    /** 内容摘要 */
    private String contentSha256;
}
