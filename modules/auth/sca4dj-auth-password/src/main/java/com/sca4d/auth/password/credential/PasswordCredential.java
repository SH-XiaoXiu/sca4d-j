package com.sca4d.auth.password.credential;

import com.sca4d.auth.password.PasswordAuthenticationMethod;
import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Account 绑定的不可逆 Argon2id 密码凭据，仅由 {@link PasswordAuthenticationMethod} 读取摘要并完成验证。
 *
 * <p>该类型不能用于 HTTP 响应或日志，避免不可逆摘要离开 Password 模块。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class PasswordCredential {

    private final Long accountId;

    private final String passwordHash;

    private final Version version;

    private final @NotNull Instant updatedAt;

    /**
     * 创建账号的密码凭据。
     *
     * @param accountId 密码凭据所属账号
     * @param passwordHash Argon2id 不可逆摘要
     * @param updatedAt 凭据最近更新时间
     * @param version 凭据版本
     */
    public PasswordCredential(
            @NotNull Long accountId,
            @NotNull String passwordHash,
            @NotNull Instant updatedAt,
            @NotNull Version version) {
        this.accountId = Objects.requireNonNull(accountId, "密码凭据账号不能为空");
        this.passwordHash = Objects.requireNonNull(passwordHash, "密码摘要不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "密码凭据更新时间不能为空");
        this.version = Objects.requireNonNull(version, "密码凭据版本不能为空");
    }

    /**
     * 返回密码凭据所属账号。
     *
     * @return 密码凭据所属账号
     */
    public @NotNull Long accountId() {
        return accountId;
    }

    /** 仅供 Password 认证方法校验，不得返回给 Controller 或日志。 */
    @ApiStatus.Internal
    @NotNull
    public String passwordHash() {
        return passwordHash;
    }

    /**
     * 返回密码凭据最近更新时间。
     *
     * @return 最近一次设置密码的时间
     */
    public @NotNull Instant updatedAt() {
        return updatedAt;
    }

    /**
     * 返回密码凭据版本。
     *
     * @return 密码凭据版本
     */
    public @NotNull Version version() {
        return version;
    }
}
