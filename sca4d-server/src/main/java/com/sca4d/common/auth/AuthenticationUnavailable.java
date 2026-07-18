package com.sca4d.common.auth;

import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.permission.SessionPermission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 没有注册认证实现时使用的拒绝登录实现。
 *
 * <p>应用没有提供认证实现时，所有登录要求都会被视为未登录。
 */
public class AuthenticationUnavailable implements Authentication {

    @Override
    public @NotNull Session createSession(
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions) {
        throw new UnauthenticatedException(CommonErrorCode.AUTHENTICATION_IMPLEMENTATION_UNAVAILABLE);
    }

    @Override
    public @NotNull Session createSession(
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions,
            @Nullable String deviceId,
            @Nullable String terminalName) {
        throw new UnauthenticatedException(CommonErrorCode.AUTHENTICATION_IMPLEMENTATION_UNAVAILABLE);
    }

    @Override
    public void logout() {
        throw new UnauthenticatedException(CommonErrorCode.AUTHENTICATION_IMPLEMENTATION_UNAVAILABLE);
    }

    @Override
    public @Nullable Session currentSession() {
        return null;
    }

    @Override
    public void replaceSession(
            @NotNull Long accountId,
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions) {
        throw new UnauthenticatedException(CommonErrorCode.AUTHENTICATION_IMPLEMENTATION_UNAVAILABLE);
    }

    @Override
    public @NotNull List<Long> onlineAccountIds() {
        return List.of();
    }

    @Override
    public void endSessions(@NotNull Long accountId) {
        throw new UnauthenticatedException(CommonErrorCode.AUTHENTICATION_IMPLEMENTATION_UNAVAILABLE);
    }

    @Override
    public @NotNull List<SessionSnapshot> sessions(@NotNull Long accountId) {
        return List.of();
    }

    @Override
    public void endSession(@NotNull Long accountId, @NotNull String sessionId) {
        throw new UnauthenticatedException(CommonErrorCode.AUTHENTICATION_IMPLEMENTATION_UNAVAILABLE);
    }
}
