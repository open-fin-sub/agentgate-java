package com.abchina.llmalf.agentgate.dao;

import com.abchina.llmalf.agentgate.dao.entity.RunAssetRefEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Run 资产引用 DAO (MyBatis-Plus Mapper).
 *
 * <p>规范:单表 SQL 薄层,业务规则归 Logic;锁定读/索引查询走同包 XML。</p>
 */
@Mapper
public interface RunAssetRefDAO extends BaseMapper<RunAssetRefEntity> {

    /**
     * 按资产查询摘要索引查询.
     *
     * @param assetLookupKey 参数
     * @return 实体列表
     */
    List<RunAssetRefEntity> selectByAssetLookupKey(@Param("assetLookupKey") byte[] assetLookupKey);

    /**
     * 按 Run 摘要查询.
     *
     * @param runKey 参数
     * @return 实体列表
     */
    List<RunAssetRefEntity> selectByRunKey(@Param("runKey") byte[] runKey);


    /**
     * 按主键查询(BINARY 主键不走 MP selectById,数组参数绑定不可靠).
     *
     * @param referenceKey 引用主键摘要
     * @return 实体(可空)
     */
    RunAssetRefEntity selectByKey(@Param("referenceKey") byte[] referenceKey);

    /**
     * 按主键删除.
     *
     * @param referenceKey 引用主键摘要
     * @return 影响行数
     */
    int deleteByKey(@Param("referenceKey") byte[] referenceKey);
}
