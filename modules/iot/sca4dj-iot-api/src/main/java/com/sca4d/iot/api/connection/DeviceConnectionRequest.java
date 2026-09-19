package com.sca4d.iot.api.connection;

import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.DeviceAccessMethod;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** 保存一次协议无关 Device 连接准入所需的公开身份和一种互斥认证输入。 */
public final class DeviceConnectionRequest {

    private final @NotNull DeviceConnectionBinding connection;

    private final @NotNull DeviceAccessMethod accessMethod;

    private final @Nullable AuthenticationSubmission<?> authentication;

    private final @Nullable String sessionToken;

    private DeviceConnectionRequest(
            @NotNull DeviceConnectionBinding connection,
            @NotNull DeviceAccessMethod accessMethod,
            @Nullable AuthenticationSubmission<?> authentication,
            @Nullable String sessionToken) {
        this.connection = Objects.requireNonNull(connection, "Device 连接绑定不能为空");
        this.accessMethod = Objects.requireNonNull(accessMethod, "Device 接入方式不能为空");
        this.authentication = authentication;
        this.sessionToken = sessionToken;
    }

    /** @return 不提交 Account 认证材料的连接请求 */
    @CheckReturnValue
    public static @NotNull DeviceConnectionRequest anonymous(
            @NotNull DeviceConnectionBinding connection) {
        return new DeviceConnectionRequest(
                connection,
                DeviceAccessMethod.anonymous(),
                null,
                null);
    }

    /** @return 使用一种通用认证方法直接验证 Account 的连接请求 */
    @CheckReturnValue
    public static @NotNull DeviceConnectionRequest authenticated(
            @NotNull DeviceConnectionBinding connection,
            @NotNull AuthenticationSubmission<?> authentication) {
        AuthenticationSubmission<?> checked = Objects.requireNonNull(authentication, "认证提交不能为空");
        return new DeviceConnectionRequest(
                connection,
                DeviceAccessMethod.authentication(checked.methodCode()),
                checked,
                null);
    }

    /** @return 使用已经建立的通用 Session Token 的连接请求 */
    @CheckReturnValue
    public static @NotNull DeviceConnectionRequest session(
            @NotNull DeviceConnectionBinding connection,
            @NotNull String sessionToken) {
        return new DeviceConnectionRequest(
                connection,
                DeviceAccessMethod.session(),
                null,
                TextUtils.requireText(sessionToken, "Session Token"));
    }

    /** @return 本次请求声明的连接协议、身份类型和公开值 */
    @Contract(pure = true)
    public @NotNull DeviceConnectionBinding connection() {
        return connection;
    }

    /** @return 本次请求唯一选择的连接方式 */
    @Contract(pure = true)
    public @NotNull DeviceAccessMethod accessMethod() {
        return accessMethod;
    }

    /** @return 直接 Account 认证提交；匿名或 Session 方式为空 */
    @Contract(pure = true)
    public @NotNull Optional<AuthenticationSubmission<?>> authentication() {
        return Optional.ofNullable(authentication);
    }

    /** @return Session Token；其他方式为空 */
    @Contract(pure = true)
    public @NotNull Optional<String> sessionToken() {
        return Optional.ofNullable(sessionToken);
    }
}
