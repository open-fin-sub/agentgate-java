package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.dao.entity.UserEntity;
import com.abchina.llmalf.agentgate.service.vo.UserQueryVO;
import com.abchina.llmalf.agentgate.service.vo.UserSaveVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.abchina.llmalf.agentgate.dao.UserDAO;
import com.abchina.llmalf.agentgate.enums.UserConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 用户业务逻辑层.
 *
 * <p>规范:Logic 层负责复杂业务规则、跨表聚合、事务边界;Service 调用 Logic.</p>
 */
@Component
public class UserLogic {

    @Autowired
    private UserDAO userDAO;

    /**
     * 校验用户名唯一性.
     *
     * @param username 用户名
     * @param excludeId 排除的 id(更新时排除自身)
     */
    public void checkUsernameUnique(String username, Long excludeId) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username);
        if (excludeId != null) {
            wrapper.ne(UserEntity::getId, excludeId);
        }
        Long count = userDAO.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new AgentException("用户名已存在: " + username);
        }
    }

    /**
     * 组装实体(从入参 VO),校验默认值.
     */
    public UserEntity buildEntity(UserSaveVO vo) {
        UserEntity entity = new UserEntity();
        entity.setUsername(vo.getUsername());
        entity.setNickname(vo.getNickname());
        entity.setEmail(vo.getEmail());
        entity.setPhone(vo.getPhone());
        entity.setStatus(vo.getStatus() == null ? UserConstants.STATUS_ENABLED : vo.getStatus());
        return entity;
    }

    /**
     * 校验用户是否存在(未删除).
     */
    public UserEntity requireExists(Long id) {
        UserEntity entity = userDAO.selectById(id);
        if (entity == null) {
            throw new AgentException("用户不存在: id=" + id);
        }
        return entity;
    }

    /**
     * 复杂分页查询(走 XML).
     */
    public IPage<UserEntity> page(UserQueryVO vo) {
        int pageNum = vo.getPageNum() == null || vo.getPageNum() < 1 ? 1 : vo.getPageNum();
        int pageSize = vo.getPageSize() == null || vo.getPageSize() < 1 ? 10 : vo.getPageSize();
        Page<UserEntity> page = new Page<>(pageNum, pageSize);
        return userDAO.selectUserPage(page, vo.getUsername(), vo.getStatus());
    }

    /**
     * 校验状态合法性.
     */
    public void checkStatus(Integer status) {
        if (status != null && !Objects.equals(status, UserConstants.STATUS_ENABLED)
                && !Objects.equals(status, UserConstants.STATUS_DISABLED)) {
            throw new AgentException("状态值非法: " + status);
        }
    }
}
