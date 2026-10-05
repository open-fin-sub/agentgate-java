package com.abchina.llmalf.agentgate.dao;

import com.abchina.llmalf.agentgate.dao.entity.TargetDescriptorEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 目标描述符 DAO (MyBatis-Plus Mapper).
 *
 * <p>规范:单表 SQL 薄层,业务规则归 Logic;锁定读/索引查询走同包 XML。</p>
 */
@Mapper
public interface TargetDescriptorDAO extends BaseMapper<TargetDescriptorEntity> {

    /**
     * 按目标身份摘要索引查询.
     *
     * @param targetRefKey 参数
     * @return 实体列表
     */
    java.util.List<TargetDescriptorEntity> selectByTargetRefKey(@Param("targetRefKey") byte[] targetRefKey);

    /**
     * 按主键锁定读.
     *
     * @param contentSha256 参数
     * @return 实体(可空)
     */
    TargetDescriptorEntity selectByIdForUpdate(@Param("contentSha256") String contentSha256);

}
