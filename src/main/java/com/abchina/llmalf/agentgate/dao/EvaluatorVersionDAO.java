package com.abchina.llmalf.agentgate.dao;

import com.abchina.llmalf.agentgate.dao.entity.EvaluatorVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 评测器发布版本 DAO (复合主键,全 XML,不继承 BaseMapper).
 *
 * <p>规范:单表 SQL 薄层,业务规则归 Logic;锁定读/索引查询走同包 XML。</p>
 */
@Mapper
public interface EvaluatorVersionDAO {

    /**
     * 插入发布版本(版本不可变,无 update).
     *
     * @param entity 参数
     * @return 影响行数
     */
    int insert(@Param("entity") EvaluatorVersionEntity entity);

    /**
     * 按评测器查全部版本.
     *
     * @param evaluatorKey 参数
     * @return 实体列表
     */
    java.util.List<EvaluatorVersionEntity> selectByEvaluatorKey(@Param("evaluatorKey") byte[] evaluatorKey);

    /**
     * 按复合主键查询.
     *
     * @param evaluatorKey 参数
     * @param version 参数
     * @return 实体(可空)
     */
    EvaluatorVersionEntity selectByEvaluatorKeyAndVersion(@Param("evaluatorKey") byte[] evaluatorKey, @Param("version") Long version);

}
