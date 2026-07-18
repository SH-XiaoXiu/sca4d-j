package com.sca4d.common.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** 不包含 Token、权限和认证材料的活跃会话管理快照。 */
public class SessionSnapshot {

    private final String sessionId;
    private final Long accountId;
    private final String deviceId;
    private final String terminalName;
    private final Instant loginTime;

    /**
     * 创建一个从认证实现当前终端数据裁剪的安全会话快照。
     *
     * @param sessionId 项目生成且不能用于认证的固定会话标识
     * @param accountId 本次认证固定使用的 Account 主键
     * @param deviceId 客户端提交的设备标识；未提供时为 null
     * @param terminalName 面向用户的终端名称；未提供时为 null
     * @param loginTime 认证实现记录的终端创建时间
     */
    public SessionSnapshot(
            @NotNull String sessionId,
            @NotNull Long accountId,
            @Nullable String deviceId,
            @Nullable String terminalName,
            @NotNull Instant loginTime) {
        this.sessionId = sessionId;
        this.accountId = accountId;
        this.deviceId = deviceId;
        this.terminalName = terminalName;
        this.loginTime = loginTime;
    }

    /** @return 不能用于认证的项目会话标识 */
    public @NotNull String getSessionId() {
        return sessionId;
    }

    /** @return 本次认证固定使用的 Account 主键 */
    public @NotNull Long getAccountId() {
        return accountId;
    }

    /** @return 客户端设备标识，未提供时为 null */
    public @Nullable String getDeviceId() {
        return deviceId;
    }

    /** @return 终端展示名称，未提供时为 null */
    public @Nullable String getTerminalName() {
        return terminalName;
    }

    /** @return 会话登录时间 */
    public @NotNull Instant getLoginTime() {
        return loginTime;
    }
}
