package com.abchina.llmalf.agentgate.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评测器发布版本(agentgate_evaluator_versions).
 *
 * <p>纯行映射(薄列 + payload),对照 Python storage/mysql_schema.py;
 * agentgate_* 表无逻辑删除,不使用 @TableLogic。复合主键表:不标注 @TableId,DAO 走全 XML(不继承 BaseMapper)。</p>
 */
@Data
@TableName("agentgate_evaluator_versions")
public class EvaluatorVersionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评测器摘要(复合主键) */
    private byte[] evaluatorKey;

    /** 版本号(复合主键) */
    private Long version;

    /** 评测器 id */
    private String evaluatorId;

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
