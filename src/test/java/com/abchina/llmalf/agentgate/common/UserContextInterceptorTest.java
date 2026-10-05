package com.abchina.llmalf.agentgate.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 用户上下文拦截器测试.
 *
 * <p>对齐 Python UserContextMiddleware:header 透传 + 默认值 +
 * 请求结束清理。</p>
 */
class UserContextInterceptorTest {

    private final UserContextInterceptor interceptor = new UserContextInterceptor();

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void preHandleBindsHeadersAndAfterCompletionClears() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("user_team_id", "team-9");
        request.addHeader("user_id", "u-9");
        request.addHeader("user_name", "张三");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        UserContext context = UserContextHolder.current();
        assertEquals("team-9", context.userTeamId());
        assertEquals("u-9", context.userId());
        assertEquals("张三", context.userName());

        interceptor.afterCompletion(request, response, new Object(), null);
        UserContext after = UserContextHolder.current();
        assertEquals("", after.userTeamId(), "context must be cleared after completion");
        assertEquals("anonymous", after.userId());
    }

    @Test
    void preHandleAppliesDefaultsWhenHeadersMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        UserContext context = UserContextHolder.current();
        assertEquals("", context.userTeamId());
        assertEquals("anonymous", context.userId());
        assertEquals("匿名用户", context.userName());

        interceptor.afterCompletion(request, response, new Object(), null);
    }

    @Test
    void currentReturnsDefaultsWhenUnbound() {
        UserContext context = UserContextHolder.current();
        assertEquals("", context.userTeamId());
        assertEquals("anonymous", context.userId());
        assertEquals("匿名用户", context.userName());
    }
}
