package com.abchina.llmalf.agentgate.dao;

import com.abchina.llmalf.agentgate.dao.entity.EvaluatorDraftEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 评测器配置草稿 DAO (MyBatis-Plus Mapper).
 *
 * <p>规范:单表 SQL 薄层,业务规则归 Logic;锁定读/索引查询走同包 XML。</p>
 */
@Mapper
public interface EvaluatorDraftDAO extends BaseMapper<EvaluatorDraftEntity> {

    /**
     * 按评测器+团队查唯一草稿.
     *
     * @param evaluatorKey 参数
     * @param userTeamKey 参数
     * @return 实体(可空)
     */
    EvaluatorDraftEntity selectByEvaluatorKeyTeamKey(@Param("evaluatorKey") byte[] evaluatorKey, @Param("userTeamKey") byte[] userTeamKey);

    /**
     * 按团队查询.
     *
     * @param userTeamKey 参数
     * @return 实体列表
     */
    java.util.List<EvaluatorDraftEntity> selectByTeamKey(@Param("userTeamKey") byte[] userTeamKey);

    /**
     * 按主键锁定读.
     *
     * @param idKey 参数
     * @return 实体(可空)
     */
    EvaluatorDraftEntity selectByIdForUpdate(@Param("idKey") byte[] idKey);


    /**
     * 按主键查询(BINARY 主键不走 MP selectById,数组参数绑定不可靠).
     *
     * @param idKey 主键摘要
     * @return 实体(可空)
     */
    EvaluatorDraftEntity selectByKey(@Param("idKey") byte[] idKey);

    /**
     * 按主键删除.
     *
     * @param idKey 主键摘要
     * @return 影响行数
     */
    int deleteByKey(@Param("idKey") byte[] idKey);
}
