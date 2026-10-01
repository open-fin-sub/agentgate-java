package com.abchina.llmalf.agentgate.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * POST 请求日志过滤器.
 *
 * <p>语义对齐 Python RequestLogMiddleware:记录方法/路径/user_id/IP,
 * /v1/traces 跳过;不记录任何凭据值.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    /** 跳过日志的路径(OTLP 上报) */
    private static final String SKIP_PATH = "/v1/traces";

    /** 应用内路径解析(剥离 context-path) */
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = PATH_HELPER.getPathWithinApplication(request);
        if ("POST".equals(request.getMethod()) && !SKIP_PATH.equals(path)) {
            String userId = request.getHeader("user_id");
            log.info("API request: {} {} user_id={} ip={}",
                    request.getMethod(),
                    path,
                    (userId == null || userId.isEmpty()) ? "anonymous" : userId,
                    request.getRemoteAddr());
        }
        chain.doFilter(request, response);
    }
}
