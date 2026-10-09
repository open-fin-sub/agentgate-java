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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * API 请求日志过滤器.
 *
 * <p>记录调用前后的方法、路径、参数名、内容长度、状态与耗时。
 * /v1/traces 为高频上报路径继续跳过;不记录查询值、请求体或凭据.</p>
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
        if (SKIP_PATH.equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        String method = request.getMethod();
        String userId = safeHeader(request.getHeader("user_id"), "anonymous");
        String teamId = safeHeader(request.getHeader("user_team_id"), "");
        String parameterNames = safeParameterNames(request);
        long startedAt = System.nanoTime();
        log.info("API request started: method={} path={} parameter_names={} content_length={} "
                        + "user_id={} user_team_id={} ip={}",
                method, path, parameterNames, request.getContentLengthLong(), userId, teamId,
                request.getRemoteAddr());
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            log.info("API request completed: method={} path={} status={} elapsed_ms={}",
                    method, path, response.getStatus(), elapsedMillis);
        }
    }

    private static String safeParameterNames(HttpServletRequest request) {
        List<String> names = new ArrayList<>();
        for (String name : request.getParameterMap().keySet()) {
            names.add(safeLogValue(name, ""));
        }
        Collections.sort(names);
        return names.toString();
    }

    private static String safeHeader(String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : safeLogValue(value, defaultValue);
    }

    private static String safeLogValue(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String sanitized = value.replace('\r', '_').replace('\n', '_');
        return sanitized.length() <= 128 ? sanitized : sanitized.substring(0, 128);
    }
}
