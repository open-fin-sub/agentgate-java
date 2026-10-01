package com.abchina.llmalf.agentgate.user.controller;

import com.abchina.llmalf.agentgate.common.ResponseBase;
import com.abchina.llmalf.agentgate.user.service.vo.UserQueryVO;
import com.abchina.llmalf.agentgate.user.service.vo.UserSaveVO;
import com.abchina.llmalf.agentgate.user.service.vo.UserVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.abchina.llmalf.agentgate.user.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 用户 REST 接口.
 *
 * <p>规范:Controller 负责参数校验、调用 Service、返回 ResponseBase.</p>
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private IUserService userService;

    /** 新增用户 */
    @PostMapping
    public ResponseBase<Long> create(@RequestBody @Valid UserSaveVO vo) {
        return ResponseBase.success(userService.create(vo));
    }

    /** 修改用户 */
    @PutMapping("/{id}")
    public ResponseBase<Void> update(@PathVariable("id") Long id,
                                     @RequestBody @Valid UserSaveVO vo) {
        userService.update(id, vo);
        return ResponseBase.success();
    }

    /** 删除用户(逻辑删除) */
    @DeleteMapping("/{id}")
    public ResponseBase<Void> delete(@PathVariable("id") Long id) {
        userService.delete(id);
        return ResponseBase.success();
    }

    /** 查询单个用户 */
    @GetMapping("/{id}")
    public ResponseBase<UserVO> get(@PathVariable("id") Long id) {
        return ResponseBase.success(userService.getById(id));
    }

    /** 分页查询用户 */
    @GetMapping("/page")
    public ResponseBase<IPage<UserVO>> page(UserQueryVO vo) {
        return ResponseBase.success(userService.page(vo));
    }
}
