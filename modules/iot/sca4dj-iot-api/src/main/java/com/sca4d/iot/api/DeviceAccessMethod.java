package com.sca4d.iot.api;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * 表示 Device 允许使用的匿名、Session 或 Account 认证连接方式。
 */
public class DeviceAccessMethod {

    /**
     * 不提交 Account 认证材料的匿名连接方式编码。
     */
    public static final @NotNull String ANONYMOUS_CODE = "ANONYMOUS";

    /**
     * 使用已经建立的通用 Session Token 连接的方式编码。
     */
    public static final @NotNull String SESSION_CODE = "SESSION";

    private final @Nullable TreeCode authenticationMethodCode;

    private final @NotNull TreeCode code;

    private DeviceAccessMethod(
            @NotNull TreeCode code,
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
        return new DeviceAccessMethod(TreeCode.of(ANONYMOUS_CODE), null);
    }

    /**
     * @return 使用通用 Session Token 的连接方式
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull DeviceAccessMethod session() {
        return new DeviceAccessMethod(TreeCode.of(SESSION_CODE), null);
    }

    /**
     * 创建直接使用一种通用 Account 认证方法的连接方式。
     *
     * @param methodCode 应用已经安装的认证方法编码
     * @return 编码为 {@code ACCOUNT:AUTHENTICATION:<method>} 的连接方式
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull DeviceAccessMethod authentication(
            @NotNull TreeCode methodCode) {
        TreeCode checked = Objects.requireNonNull(methodCode, "认证方法编码不能为空");
        return new DeviceAccessMethod(TreeCode.of("ACCOUNT").append(checked), checked);
    }

    /**
     * 解析数据库、配置或协议提交的稳定连接方式编码。
     *
     * @param code {@code ANONYMOUS}、{@code SESSION} 或 {@code ACCOUNT:AUTHENTICATION:<method>}
     * @return 类型化连接方式
     * @throws IllegalArgumentException 编码不属于任一允许形状时抛出
     */
    @CheckReturnValue
    public static @NotNull DeviceAccessMethod parse(@NotNull String code) {
        String text = Objects.requireNonNull(code, "Device 接入方式编码不能为空");
        TreeCode checked;
        int slash = text.indexOf('/');
        if (slash >= 0 && text.indexOf(':') >= 0) {
            if (text.indexOf('/', slash + 1) >= 0) {
                throw new IllegalArgumentException("Device Account 认证方法编码格式无效");
            }
            TreeCode prefix = TreeCode.ofColon(text.substring(0, slash));
            TreeCode suffix = TreeCode.ofSlash(text.substring(slash + 1));
            checked = prefix.append(suffix);
        } else {
            checked = TreeCode.of(text);
        }
        if (checked.equals(TreeCode.of(ANONYMOUS_CODE))) {
            return anonymous();
        }
        if (checked.equals(TreeCode.of(SESSION_CODE))) {
            return session();
        }
        if (checked.size() < 3 || !checked.isUnder(TreeCode.of("ACCOUNT:AUTHENTICATION"))) {
            throw new IllegalArgumentException("Device Account 认证方法编码无效");
        }
        TreeCode methodCode = TreeCode.ofSegment(checked.nodes().get(1));
        for (String node : checked.nodes().subList(2, checked.size())) {
            methodCode = methodCode.child(node);
        }
        return authentication(methodCode);
    }

    /**
     * @return 数据库、配置和协议共同使用的稳定编码
     */
    @Contract(pure = true)
    public @NotNull TreeCode code() {
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
        return TreeCode.of(SESSION_CODE).equals(code);
    }

    /**
     * @return 当前方式是否不要求 Account 认证
     */
    @Contract(pure = true)
    public boolean anonymousAccess() {
        return TreeCode.of(ANONYMOUS_CODE).equals(code);
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
        return code.toString();
    }
}
