package com.abchina.llmalf.agentgate.common;

/**
 * 请求用户上下文.
 *
 * <p>对齐 Python server/app.py::UserContextMiddleware:
 * header user_team_id(默认空)/user_id(默认 anonymous)/
 * user_name(默认 匿名用户),仅透传不认证。</p>
 */
public final class UserContext {

    private final String userTeamId;
    private final String userId;
    private final String userName;

    public UserContext(String userTeamId, String userId, String userName) {
        this.userTeamId = userTeamId;
        this.userId = userId;
        this.userName = userName;
    }

    public String userTeamId() {
        return userTeamId;
    }

    public String userId() {
        return userId;
    }

    public String userName() {
        return userName;
    }
}
