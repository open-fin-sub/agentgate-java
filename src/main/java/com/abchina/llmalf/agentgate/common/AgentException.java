package com.abchina.llmalf.agentgate.common;

import lombok.Getter;

/**
 * 业务异常.
 *
 * <p>规范:业务异常抛 AgentException,全局由 GlobalExceptionHandler 捕获.
 * httpStatus 对应 Python HTTPException 的 status_code(默认 400),
 * detail 为可选的结构化载荷(对应 Python raise_xlsx_error 等结构化 detail).</p>
 */
@Getter
public class AgentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 默认 HTTP 状态码(对齐 FastAPI HTTPException 默认值) */
    public static final int DEFAULT_STATUS = 400;

    private final int httpStatus;

    /** 结构化错误载荷(可选,置入响应信封 data 字段) */
    private final transient Object detail;

    public AgentException(String message) {
        this(DEFAULT_STATUS, message, null);
    }

    public AgentException(int httpStatus, String message) {
        this(httpStatus, message, null);
    }

    public AgentException(int httpStatus, String message, Object detail) {
        super(message);
        this.httpStatus = httpStatus;
        this.detail = detail;
    }

    public AgentException(int httpStatus, String message, Object detail, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.detail = detail;
    }
}
