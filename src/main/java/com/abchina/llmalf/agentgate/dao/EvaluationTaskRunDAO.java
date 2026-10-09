package com.abchina.llmalf.agentgate.dao;

import com.abchina.llmalf.agentgate.dao.entity.EvaluationTaskRunEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评测任务-Run 关联 DAO (复合主键,全 XML,不继承 BaseMapper).
 *
 * <p>规范:单表 SQL 薄层,业务规则归 Logic;锁定读/索引查询走同包 XML。</p>
 */
@Mapper
public interface EvaluationTaskRunDAO {

    /**
     * 插入关联.
     *
     * @param entity 参数
     * @return 影响行数
     */
    int insert(@Param("entity") EvaluationTaskRunEntity entity);

    /**
     * 按 Run 摘要查询(主键前缀).
     *
     * @param runKey 参数
     * @return 实体(可空)
     */
    EvaluationTaskRunEntity selectByRunKey(@Param("runKey") byte[] runKey);

    /**
     * 按任务摘要查询.
     *
     * @param taskKey 参数
     * @return 实体列表
     */
    List<EvaluationTaskRunEntity> selectByTaskKey(@Param("taskKey") byte[] taskKey);

    /**
     * 按任务摘要删除.
     *
     * @param taskKey 参数
     * @return 影响行数
     */
    int deleteByTaskKey(@Param("taskKey") byte[] taskKey);

    /**
     * 按 Run 摘要删除.
     *
     * @param runKey 参数
     * @return 影响行数
     */
    int deleteByRunKey(@Param("runKey") byte[] runKey);

}
