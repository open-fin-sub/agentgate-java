package com.abchina.llmalf.agentgate.common;

/**
 * 请求线程用户上下文持有器.
 *
 * <p>Interceptor 写入/清理,Service 层读取;对应 Python contextvar
 * set_user_info/reset_user_info。异步线程不自动传递。</p>
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    /**
     * 绑定上下文.
     *
     * @param context 用户上下文
     */
    public static void set(UserContext context) {
        CONTEXT.set(context);
    }

    /**
     * 读取上下文(未绑定时返回默认匿名上下文).
     *
     * @return 用户上下文
     */
    public static UserContext current() {
        UserContext context = CONTEXT.get();
        if (context == null) {
            return new UserContext("", "anonymous", "匿名用户");
        }
        return context;
    }

    /**
     * 清理上下文(请求结束必须调用).
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
