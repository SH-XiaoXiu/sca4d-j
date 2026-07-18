package com.sca4d.authentication.satoken;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpUtil;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Session;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.permission.SessionPermission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sa-Token 会话运行时句柄。
 */
public final class SaTokenSession implements Session {

    /** 项目生成的固定会话标识，用于管理面展示和关联，不等同于 bearer token。 */
    private final String sessionId;

    /** Sa-Token 在当前应用中使用的 token 名称。 */
    private final String tokenName;

    /** 关闭 Sa-Token 登录态所需的 bearer token，只在运行时句柄内部保存。 */
    private final String tokenValue;

    /** 创建或恢复该会话时一次装载的登录 Subject。 */
    private final Subject subject;

    /** 本次认证固定使用的 Account。 */
    private final Account account;

    /** 当前 Subject 可选择的全部 Principal。 */
    private final List<Principal> principals;

    /** 以 Principal 主键索引的角色和权限。 */
    private final Map<Long, SessionPermission> permissions;

    /** Sa-Token 记录的登录设备标识；未知设备不提供该值。 */
    private final String deviceId;

    /** 调用方提交的终端展示名称；未提交时不提供该值。 */
    private final String terminalName;

    /** Sa-Token 记录的登录时间；历史会话缺少该信息时不提供该值。 */
    private final Instant loginTime;

    /** token 的绝对过期时间；永不过期 token 不提供该值。 */
    private final Instant expireTime;

    /** 保护 invalidate() 幂等性的本地状态，避免并发失效重复踢出同一 token。 */
    private final AtomicBoolean invalidated = new AtomicBoolean();

    /**
     * 创建 Sa-Token Session 句柄。
     *
     * @param sessionId 项目生成的固定会话标识
     * @param tokenName Sa-Token 在 HTTP 传输中使用的 token 名称
     * @param tokenValue 用于定位并关闭 Sa-Token 登录态的 bearer token
     * @param subject 已完成认证的 Subject
     * @param account 已完成认证且固定于当前 Session 的 Account
     * @param principals 当前可选择的全部 Principal
     * @param permissions 每个 Principal 的角色和权限
     * @param deviceId Sa-Token 记录的设备标识；未知时不提供
     * @param terminalName 终端展示名称；调用方未提交时不提供
     * @param loginTime 登录时间；历史会话缺少记录时不提供
     * @param expireTime token 过期时间；永不过期 token 不提供
     */
    public SaTokenSession(
            @NotNull String sessionId,
            @NotNull String tokenName,
            @NotNull String tokenValue,
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions,
            @Nullable String deviceId,
            @Nullable String terminalName,
            @Nullable Instant loginTime,
            @Nullable Instant expireTime) {
        this.sessionId = sessionId;
        this.tokenName = tokenName;
        this.tokenValue = tokenValue;
        this.subject = subject;
        this.account = account;
        this.principals = List.copyOf(principals);
        this.permissions = Map.copyOf(permissions);
        this.deviceId = deviceId;
        this.terminalName = terminalName;
        this.loginTime = loginTime;
        this.expireTime = expireTime;
    }

    @Override
    public @NotNull String getSessionId() {
        return sessionId;
    }

    @Override
    public @NotNull String getTokenName() {
        return tokenName;
    }

    @Override
    public @NotNull String getTokenValue() {
        return tokenValue;
    }

    @Override
    public @NotNull Subject getSubject() {
        return subject;
    }

    @Override
    public @NotNull Account getAccount() {
        return account;
    }

    @Override
    public @NotNull List<Principal> getPrincipals() {
        return principals;
    }

    @Override
    public @NotNull Map<Long, SessionPermission> getPermissions() {
        return permissions;
    }

    @Override
    public @Nullable String getDeviceId() {
        return deviceId;
    }

    @Override
    public @Nullable String getTerminalName() {
        return terminalName;
    }

    @Override
    public @Nullable Instant getLoginTime() {
        return loginTime;
    }

    @Override
    public @Nullable Instant getExpireTime() {
        return expireTime;
    }

    @Override
    public void invalidate() {
        if (!invalidated.compareAndSet(false, true)) {
            return;
        }
        try {
            StpUtil.kickoutByTokenValue(tokenValue);
        } catch (SaTokenException ignored) {
            // Sa-Token 已不存在该 token 时，关闭含义仍然成立。
        }
    }
}
