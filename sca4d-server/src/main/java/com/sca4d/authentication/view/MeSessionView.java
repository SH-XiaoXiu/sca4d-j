package com.sca4d.authentication.view;

import com.sca4d.common.auth.Session;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * 当前会话状态视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeSessionView {

    private String tokenName;
    private String deviceId;
    private String terminalName;
    private Instant loginTime;
    private Instant expireTime;

    /**
     * 从当前会话创建视图。
     *
     * @param session 当前会话
     * @return 当前会话状态视图
     */
    public static @Nullable MeSessionView from(@Nullable Session session) {
        if (session == null) {
            return null;
        }
        return new MeSessionView(
                session.getTokenName(),
                session.getDeviceId(),
                session.getTerminalName(),
                session.getLoginTime(),
                session.getExpireTime());
    }
}
