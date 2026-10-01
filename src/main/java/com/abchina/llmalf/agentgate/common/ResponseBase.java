package com.abchina.llmalf.agentgate.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回格式.
 *
 * <p>规范:统一使用 ResponseBase(code/message/data),code="0" 为成功.</p>
 *
 * @param <T> 数据载荷类型
 */
@Data
public class ResponseBase<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功状态码 */
    public static final String CODE_SUCCESS = "0";

    /** 默认失败状态码 */
    public static final String CODE_FAIL = "-1";

    /** 系统异常状态码 */
    public static final String CODE_SYSTEM_ERROR = "500";

    private String code;
    private String message;
    private T data;

    public static <T> ResponseBase<T> success(T data) {
        ResponseBase<T> resp = new ResponseBase<>();
        resp.setCode(CODE_SUCCESS);
        resp.setMessage("success");
        resp.setData(data);
        return resp;
    }

    public static <T> ResponseBase<T> success() {
        return success(null);
    }

    public static <T> ResponseBase<T> fail(String code, String message) {
        ResponseBase<T> resp = new ResponseBase<>();
        resp.setCode(code);
        resp.setMessage(message);
        return resp;
    }

    public static <T> ResponseBase<T> fail(String message) {
        return fail(CODE_FAIL, message);
    }
}
