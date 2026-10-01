package com.abchina.llmalf.agentgate.user.service.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户分页查询入参 VO.
 */
@Data
public class UserQueryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户名(模糊匹配,可空) */
    private String username;

    /** 状态(可空) */
    private Integer status;

    /** 页码,默认 1 */
    private Integer pageNum = 1;

    /** 每页大小,默认 10 */
    private Integer pageSize = 10;
}
