package com.abchina.llmalf.agentgate.common;

import lombok.Getter;

/**
 * 业务异常.
 *
 * <p>规范:业务异常抛 AgentException,全局由 GlobalExceptionHandler 捕获.</p>
 */
@Getter
public class AgentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public AgentException(String message) {
        super(message);
        this.code = ResponseBase.CODE_FAIL;
    }

    public AgentException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AgentException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
