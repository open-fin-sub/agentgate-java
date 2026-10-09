package com.abchina.llmalf.agentgate.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理.
 *
 * <p>规范:业务异常抛 AgentException,全局由 GlobalExceptionHandler 捕获.
 * 语义对齐 Python:错误信封 code="1"、HTTP 状态码随异常透传、
 * 校验类错误统一 422、兜底异常消息经 SafeMessages 脱敏.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常:HTTP 状态码与 detail 透传 */
    @ExceptionHandler(AgentException.class)
    public ResponseEntity<ResponseBase<Object>> handleAgentException(AgentException e) {
        log.warn("业务异常: status={}, message={}", e.getHttpStatus(), e.getMessage());
        return ResponseEntity.status(e.getHttpStatus())
                .body(ResponseBase.<Object>fail(e.getMessage(), e.getDetail()));
    }

    /** RequestBody 参数校验失败(对齐 Python pydantic 422 语义) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseBase<Object>> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ":" + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return unprocessable(msg);
    }

    /** 表单参数校验失败 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ResponseBase<Object>> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", msg);
        return unprocessable(msg);
    }

    /** 单参数校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseBase<Object>> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", msg);
        return unprocessable(msg);
    }

    /** 缺少必填参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseBase<Object>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return unprocessable("缺少必填参数: " + e.getParameterName());
    }

    /** 请求体不可读 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseBase<Object>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return unprocessable("请求体格式错误");
    }

    /** 方法不支持(对齐 FastAPI 405 "Method Not Allowed") */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseBase<Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ResponseBase.<Object>fail("Method Not Allowed"));
    }

    /** 未映射路径(对齐 FastAPI 默认 404 {"detail":"Not Found"}) */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ResponseBase<Object>> handleNoHandler(
            NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResponseBase.<Object>fail("Not Found"));
    }

    /** 兜底:系统异常(消息脱敏,对齐 Python _safe_message) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseBase<Object>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseBase.<Object>fail(SafeMessages.of(e)));
    }

    private ResponseEntity<ResponseBase<Object>> unprocessable(String message) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ResponseBase.<Object>fail(message));
    }
}
