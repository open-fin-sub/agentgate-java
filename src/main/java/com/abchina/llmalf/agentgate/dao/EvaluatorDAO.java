package com.abchina.llmalf.agentgate.dao;

import com.abchina.llmalf.agentgate.dao.entity.EvaluatorEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评测器目录 DAO (MyBatis-Plus Mapper).
 *
 * <p>规范:单表 SQL 薄层,业务规则归 Logic;锁定读/索引查询走同包 XML。</p>
 */
@Mapper
public interface EvaluatorDAO extends BaseMapper<EvaluatorEntity> {

    /**
     * 按团队索引查询.
     *
     * @param userTeamKey 参数
     * @return 实体列表
     */
    List<EvaluatorEntity> selectByTeamKey(@Param("userTeamKey") byte[] userTeamKey);

    /**
     * 按主键锁定读.
     *
     * @param idKey 参数
     * @return 实体(可空)
     */
    EvaluatorEntity selectByIdForUpdate(@Param("idKey") byte[] idKey);


    /**
     * 按主键查询(BINARY 主键不走 MP selectById,数组参数绑定不可靠).
     *
     * @param idKey 主键摘要
     * @return 实体(可空)
     */
    EvaluatorEntity selectByKey(@Param("idKey") byte[] idKey);

    /**
     * 按主键删除.
     *
     * @param idKey 主键摘要
     * @return 影响行数
     */
    int deleteByKey(@Param("idKey") byte[] idKey);
}
