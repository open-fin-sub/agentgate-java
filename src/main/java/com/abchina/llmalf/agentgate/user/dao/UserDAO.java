package com.abchina.llmalf.agentgate.user.dao;

import com.abchina.llmalf.agentgate.user.dao.entity.UserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 DAO(MyBatis-Plus Mapper).
 *
 * <p>规范:DAO 层负责单表 CRUD,复杂查询走 XML(见同包 UserDAO.xml).</p>
 */
@Mapper
public interface UserDAO extends BaseMapper<UserEntity> {

    /**
     * 复杂分页查询(走 XML):支持用户名模糊匹配 + 状态过滤.
     *
     * @param page     分页对象
     * @param username 用户名(模糊,可空)
     * @param status   状态(可空)
     * @return 分页结果
     */
    IPage<UserEntity> selectUserPage(IPage<UserEntity> page,
                                     @Param("username") String username,
                                     @Param("status") Integer status);
}
