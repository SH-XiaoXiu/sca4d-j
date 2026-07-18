package com.sca4d.authentication.view;

import com.sca4d.common.auth.Session;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * 登录会话视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginSessionView {

    private String sessionId;
    private String tokenName;
    private String tokenValue;
    private Long subjectId;
    private Long accountId;
    private String deviceId;
    private String terminalName;
    private Instant loginTime;
    private Instant expireTime;

    /**
     * 从当前会话创建登录会话视图。
     *
     * @param session 当前会话
     * @return 登录会话视图
     */
    public static @NotNull LoginSessionView from(@NotNull Session session) {
        return new LoginSessionView(
                session.getSessionId(),
                session.getTokenName(),
                session.getTokenValue(),
                session.getSubjectId(),
                session.getAccountId(),
                session.getDeviceId(),
                session.getTerminalName(),
                session.getLoginTime(),
                session.getExpireTime());
    }
}
