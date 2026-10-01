package com.abchina.llmalf.agentgate.enums;

/**
 * 用户模块常量.
 *
 * <p>规范:模块常量/枚举集中在 enums 包.</p>
 */
public final class UserConstants {

    private UserConstants() {
    }

    /** 状态:禁用 */
    public static final int STATUS_DISABLED = 0;

    /** 状态:启用 */
    public static final int STATUS_ENABLED = 1;

    /** 用户名最大长度 */
    public static final int USERNAME_MAX_LEN = 50;
}
