package com.abchina.llmalf.agentgate.dao;

import com.abchina.llmalf.agentgate.dao.entity.RunEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 评测 Run DAO (MyBatis-Plus Mapper).
 *
 * <p>规范:单表 SQL 薄层,业务规则归 Logic;锁定读/索引查询走同包 XML。</p>
 */
@Mapper
public interface RunDAO extends BaseMapper<RunEntity> {

    /**
     * 按团队索引查询.
     *
     * @param userTeamKey 参数
     * @return 实体列表
     */
    java.util.List<RunEntity> selectByTeamKey(@Param("userTeamKey") byte[] userTeamKey);

    /**
     * 按状态(+可选团队)查询.
     *
     * @param status 参数
     * @param userTeamKey 参数
     * @return 实体列表
     */
    java.util.List<RunEntity> selectByStatus(@Param("status") String status, @Param("userTeamKey") byte[] userTeamKey);

    /**
     * 按主键锁定读(claim/cancel 用).
     *
     * @param idKey 参数
     * @return 实体(可空)
     */
    RunEntity selectByIdForUpdate(@Param("idKey") byte[] idKey);

    /**
     * 按状态锁定读(claim_due_scheduled_runs 用).
     *
     * @param status 参数
     * @return 实体列表
     */
    java.util.List<RunEntity> selectByStatusForUpdate(@Param("status") String status);


    /**
     * 按主键查询(BINARY 主键不走 MP selectById,数组参数绑定不可靠).
     *
     * @param idKey 主键摘要
     * @return 实体(可空)
     */
    RunEntity selectByKey(@Param("idKey") byte[] idKey);

    /**
     * 按主键删除.
     *
     * @param idKey 主键摘要
     * @return 影响行数
     */
    int deleteByKey(@Param("idKey") byte[] idKey);
}
