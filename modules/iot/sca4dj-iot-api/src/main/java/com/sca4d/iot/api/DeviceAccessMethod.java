package com.sca4d.iot.api;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * 表示 Device 允许使用的匿名、Session 或 Account 认证连接方式。
 */
public final class DeviceAccessMethod {

    /**
     * 不提交 Account 认证材料的匿名连接方式编码。
     */
    public static final @NotNull String ANONYMOUS_CODE = "ANONYMOUS";

    /**
     * 使用已经建立的通用 Session Token 连接的方式编码。
     */
    public static final @NotNull String SESSION_CODE = "SESSION";

    private static final String ACCOUNT_PREFIX = "ACCOUNT:";

    private final @NotNull String code;

    private final @Nullable TreeCode authenticationMethodCode;

    private DeviceAccessMethod(
            @NotNull String code,
            @Nullable TreeCode authenticationMethodCode) {
        this.code = code;
        this.authenticationMethodCode = authenticationMethodCode;
    }

    /**
     * @return 不要求 Account 或 Credential 的匿名连接方式
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull DeviceAccessMethod anonymous() {
        return new DeviceAccessMethod(ANONYMOUS_CODE, null);
    }

    /**
     * @return 使用通用 Session Token 的连接方式
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull DeviceAccessMethod session() {
        return new DeviceAccessMethod(SESSION_CODE, null);
    }

    /**
     * 创建直接使用一种通用 Account 认证方法的连接方式。
     *
     * @param methodCode 应用已经安装的认证方法编码
     * @return 编码为 {@code ACCOUNT:AUTHENTICATION/<method>} 的连接方式
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull DeviceAccessMethod authentication(
            @NotNull TreeCode methodCode) {
        TreeCode checked = Objects.requireNonNull(methodCode, "认证方法编码不能为空");
        return new DeviceAccessMethod(ACCOUNT_PREFIX + checked.value(), checked);
    }

    /**
     * 解析数据库、配置或协议提交的稳定连接方式编码。
     *
     * @param code {@code ANONYMOUS}、{@code SESSION} 或 {@code ACCOUNT:AUTHENTICATION/<method>}
     * @return 类型化连接方式
     * @throws IllegalArgumentException 编码不属于任一允许形状时抛出
     */
    @CheckReturnValue
    public static @NotNull DeviceAccessMethod parse(@NotNull String code) {
        String checked = TextUtils.requireText(code, "Device 接入方式编码");
        if (ANONYMOUS_CODE.equals(checked)) {
            return anonymous();
        }
        if (SESSION_CODE.equals(checked)) {
            return session();
        }
        if (!checked.startsWith(ACCOUNT_PREFIX)) {
            throw new IllegalArgumentException("Device 接入方式编码无效");
        }
        String methodCode = checked.substring(ACCOUNT_PREFIX.length());
        if (!methodCode.startsWith("AUTHENTICATION/")
                || methodCode.length() == "AUTHENTICATION/".length()
                || methodCode.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Device Account 认证方法编码无效");
        }
        return authentication(TreeCode.ofSlash(methodCode));
    }

    /**
     * @return 数据库、配置和协议共同使用的稳定编码
     */
    @Contract(pure = true)
    public @NotNull String code() {
        return code;
    }

    /**
     * @return 直接 Account 认证方式对应的方法编码；匿名或 Session 方式为空
     */
    @Contract(pure = true)
    public @NotNull Optional<TreeCode> authenticationMethodCode() {
        return Optional.ofNullable(authenticationMethodCode);
    }

    /**
     * @return 当前方式是否使用已经建立的通用 Session
     */
    @Contract(pure = true)
    public boolean usesSession() {
        return SESSION_CODE.equals(code);
    }

    /**
     * @return 当前方式是否不要求 Account 认证
     */
    @Contract(pure = true)
    public boolean anonymousAccess() {
        return ANONYMOUS_CODE.equals(code);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof DeviceAccessMethod method && code.equals(method.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public @NotNull String toString() {
        return code;
    }
}
