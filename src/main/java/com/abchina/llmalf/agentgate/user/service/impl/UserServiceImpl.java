package com.abchina.llmalf.agentgate.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.abchina.llmalf.agentgate.user.dao.UserDAO;
import com.abchina.llmalf.agentgate.user.dao.entity.UserEntity;
import com.abchina.llmalf.agentgate.user.logic.UserLogic;
import com.abchina.llmalf.agentgate.user.service.IUserService;
import com.abchina.llmalf.agentgate.user.service.vo.UserQueryVO;
import com.abchina.llmalf.agentgate.user.service.vo.UserSaveVO;
import com.abchina.llmalf.agentgate.user.service.vo.UserVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务实现.
 *
 * <p>规范:Service 编排业务,调用 Logic(复杂规则) 和 DAO(单表 CRUD);事务边界在 Service.</p>
 */
@Service
public class UserServiceImpl implements IUserService {

    @Autowired
    private UserLogic userLogic;

    @Autowired
    private UserDAO userDAO;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserSaveVO vo) {
        userLogic.checkStatus(vo.getStatus());
        userLogic.checkUsernameUnique(vo.getUsername(), null);
        UserEntity entity = userLogic.buildEntity(vo);
        userDAO.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserSaveVO vo) {
        UserEntity exists = userLogic.requireExists(id);
        userLogic.checkStatus(vo.getStatus());
        userLogic.checkUsernameUnique(vo.getUsername(), id);
        UserEntity entity = userLogic.buildEntity(vo);
        entity.setId(exists.getId());
        userDAO.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        userLogic.requireExists(id);
        userDAO.deleteById(id);
    }

    @Override
    public UserVO getById(Long id) {
        UserEntity entity = userLogic.requireExists(id);
        return toVO(entity);
    }

    @Override
    public IPage<UserVO> page(UserQueryVO vo) {
        return userLogic.page(vo).convert(this::toVO);
    }

    private UserVO toVO(UserEntity entity) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
