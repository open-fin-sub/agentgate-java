package com.abchina.llmalf.agentgate.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理.
 *
 * <p>规范:业务异常抛 AgentException,全局由 GlobalExceptionHandler 捕获,统一返回 ResponseBase.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(AgentException.class)
    public ResponseBase<?> handleAgentException(AgentException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseBase.fail(e.getCode(), e.getMessage());
    }

    /** RequestBody 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseBase<?> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ":" + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return ResponseBase.fail(ResponseBase.CODE_FAIL, msg);
    }

    /** 表单参数校验失败 */
    @ExceptionHandler(BindException.class)
    public ResponseBase<?> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", msg);
        return ResponseBase.fail(ResponseBase.CODE_FAIL, msg);
    }

    /** 单参数校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseBase<?> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", msg);
        return ResponseBase.fail(ResponseBase.CODE_FAIL, msg);
    }

    /** 缺少必填参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseBase<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return ResponseBase.fail(ResponseBase.CODE_FAIL, "缺少必填参数: " + e.getParameterName());
    }

    /** 请求体不可读 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseBase<?> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseBase.fail(ResponseBase.CODE_FAIL, "请求体格式错误");
    }

    /** 兜底:系统异常 */
    @ExceptionHandler(Exception.class)
    public ResponseBase<?> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseBase.fail(ResponseBase.CODE_SYSTEM_ERROR, "系统异常: " + e.getMessage());
    }
}
