package com.sca4d.app.server.authentication.request;

import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收 Sca4d Server 配置允许的账号标识和原始密码。 */
public final class PasswordLoginRequest {

    @NotBlank
    private @Nullable String identifier;

    @NotBlank
    private @Nullable String password;

    /** 创建供 JSON 反序列化填充的空请求。 */
    public PasswordLoginRequest() {
    }

    /**
     * 返回用于发现平台账号的登录标识。
     *
     * @return 用户名、邮箱等 Sca4d Server 已配置的登录标识
     */
    public @NotNull String getIdentifier() {
        return Objects.requireNonNull(identifier, "登录标识不能为空");
    }

    /**
     * 设置用于发现平台账号的登录标识。
     *
     * @param identifier 用户名、邮箱等 Sca4d Server 已配置的登录标识
     */
    @SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
    public void setIdentifier(@Nullable String identifier) {
        this.identifier = identifier;
    }

    /**
     * 返回原始密码。
     *
     * @return 原始密码
     */
    public @NotNull String getPassword() {
        return Objects.requireNonNull(password, "密码不能为空");
    }

    /**
     * 设置原始密码。
     *
     * @param password 原始密码
     */
    @SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    @Override
    public @NotNull String toString() {
        return "PasswordLoginRequest[identifier=" + identifier + ", password=protected]";
    }
}
