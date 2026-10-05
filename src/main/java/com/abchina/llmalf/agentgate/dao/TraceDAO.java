package com.abchina.llmalf.agentgate.dao;

import com.abchina.llmalf.agentgate.dao.entity.TraceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 执行轨迹 DAO (MyBatis-Plus Mapper).
 *
 * <p>规范:单表 SQL 薄层,业务规则归 Logic;锁定读/索引查询走同包 XML。</p>
 */
@Mapper
public interface TraceDAO extends BaseMapper<TraceEntity> {

    /**
     * 按 Run 摘要查询.
     *
     * @param runKey 参数
     * @return 实体列表
     */
    java.util.List<TraceEntity> selectByRunKey(@Param("runKey") byte[] runKey);

    /**
     * 按 Run+用例唯一键查询.
     *
     * @param runKey 参数
     * @param caseKey 参数
     * @return 实体(可空)
     */
    TraceEntity selectByRunKeyAndCaseKey(@Param("runKey") byte[] runKey, @Param("caseKey") byte[] caseKey);


    /**
     * 按主键查询(BINARY 主键不走 MP selectById,数组参数绑定不可靠).
     *
     * @param idKey 主键摘要
     * @return 实体(可空)
     */
    TraceEntity selectByKey(@Param("idKey") byte[] idKey);

    /**
     * 按主键删除.
     *
     * @param idKey 主键摘要
     * @return 影响行数
     */
    int deleteByKey(@Param("idKey") byte[] idKey);

    /**
     * 按主键锁定读(事务内使用).
     *
     * @param idKey 主键摘要
     * @return 实体(可空)
     */
    TraceEntity selectByIdForUpdate(@Param("idKey") byte[] idKey);
}
