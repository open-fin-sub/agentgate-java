package com.abchina.llmalf.agentgate.dao;

import com.abchina.llmalf.agentgate.dao.entity.SkillAnalysisReportEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 技能分析报告 DAO (MyBatis-Plus Mapper).
 *
 * <p>规范:单表 SQL 薄层,业务规则归 Logic;当前仅提供存在性查询。</p>
 */
@Mapper
public interface SkillAnalysisReportDAO extends BaseMapper<SkillAnalysisReportEntity> {

    /**
     * 按主键查询(BINARY 主键不走 MP selectById,数组参数绑定不可靠).
     *
     * @param idKey 主键摘要
     * @return 实体(可空)
     */
    SkillAnalysisReportEntity selectByKey(@Param("idKey") byte[] idKey);
}
