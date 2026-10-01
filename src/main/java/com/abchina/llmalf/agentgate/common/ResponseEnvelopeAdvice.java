package com.abchina.llmalf.agentgate.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Set;

/**
 * 响应信封包装.
 *
 * <p>语义对齐 Python ResponseEnvelopeMiddleware:JSON 响应统一包装为
 * {code="0", message="success", data=原始响应};已是 ResponseBase 的透传;
 * 空体/204、非 JSON、字节数组与字符串不包装;/v1/traces 跳过.</p>
 */
@RestControllerAdvice
public class ResponseEnvelopeAdvice implements ResponseBodyAdvice<Object> {

    /** 跳过信封包装的路径(OTLP 接收端点,对齐 Python SKIP_ENVELOPE_PATHS) */
    private static final Set<String> SKIP_PATHS = Collections.singleton("/v1/traces");

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return !ResponseBase.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            return null;
        }
        if (body instanceof ResponseBase || body instanceof byte[] || body instanceof String) {
            return body;
        }
        if (SKIP_PATHS.contains(pathWithinApplication(request))) {
            return body;
        }
        if (!isJson(selectedContentType)) {
            return body;
        }
        return ResponseBase.success(body);
    }

    /** 应用内路径解析(剥离 context-path,MockMvc 与真实容器一致) */
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private String pathWithinApplication(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            return PATH_HELPER.getPathWithinApplication(servletRequest);
        }
        return request.getURI().getPath();
    }

    private boolean isJson(MediaType contentType) {
        return contentType != null
                && (MediaType.APPLICATION_JSON.includes(contentType)
                || contentType.includes(MediaType.APPLICATION_JSON));
    }
}
