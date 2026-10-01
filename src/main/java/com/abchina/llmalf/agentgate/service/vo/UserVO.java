package com.abchina.llmalf.agentgate.service.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户视图对象(返回前端).
 *
 * <p>规范:VO 为返回前端的 DTO,不暴露 isDeleted 等内部字段.</p>
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
