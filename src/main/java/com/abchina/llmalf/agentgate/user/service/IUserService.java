package com.abchina.llmalf.agentgate.user.service;

import com.abchina.llmalf.agentgate.user.service.vo.UserQueryVO;
import com.abchina.llmalf.agentgate.user.service.vo.UserSaveVO;
import com.abchina.llmalf.agentgate.user.service.vo.UserVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 用户服务接口.
 *
 * <p>规范:Service 层负责业务编排,调用 Logic 和 DAO,不直接处理复杂规则.</p>
 */
public interface IUserService {

    /** 新增用户,返回主键 */
    Long create(UserSaveVO vo);

    /** 修改用户 */
    void update(Long id, UserSaveVO vo);

    /** 删除用户(逻辑删除) */
    void delete(Long id);

    /** 根据主键查询用户 */
    UserVO getById(Long id);

    /** 分页查询用户 */
    IPage<UserVO> page(UserQueryVO vo);
}
