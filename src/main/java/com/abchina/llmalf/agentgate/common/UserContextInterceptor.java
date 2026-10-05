package com.abchina.llmalf.agentgate.common;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 用户上下文拦截器.
 *
 * <p>对齐 Python UserContextMiddleware:从 header 读取
 * user_team_id/user_id/user_name 并绑定线程上下文,请求结束清理。</p>
 */
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        UserContext context = new UserContext(
                headerOrDefault(request, "user_team_id", ""),
                headerOrDefault(request, "user_id", "anonymous"),
                headerOrDefault(request, "user_name", "匿名用户"));
        UserContextHolder.set(context);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        UserContextHolder.clear();
    }

    private static String headerOrDefault(HttpServletRequest request, String header,
            String fallback) {
        String value = request.getHeader(header);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
