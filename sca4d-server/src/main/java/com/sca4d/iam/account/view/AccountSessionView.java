package com.sca4d.iam.account.view;

import com.sca4d.common.auth.SessionSnapshot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 当前用户可见且不能用于认证的 Account 登录 Session。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountSessionView {

    private String sessionId;
    private String deviceId;
    private String terminalName;
    private Instant loginTime;

    /** @return 不包含 Token 和权限的登录 Session 表示 */
    public static @NotNull AccountSessionView from(@NotNull SessionSnapshot session) {
        return new AccountSessionView(
                session.getSessionId(),
                session.getDeviceId(),
                session.getTerminalName(),
                session.getLoginTime());
    }
}
