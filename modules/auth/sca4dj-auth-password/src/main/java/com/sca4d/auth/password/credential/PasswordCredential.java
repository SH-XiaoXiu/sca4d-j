package com.sca4d.auth.password.credential;

import static com.sca4d.auth.password.credential.PasswordCredential.Status.ACTIVE;

import com.sca4d.auth.password.PasswordAuthenticationMethod;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.enums.BaseEnum;
import org.apache.ibatis.annotations.AutomapConstructor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Account 绑定的不可逆 Argon2id 密码凭据，仅由 {@link PasswordAuthenticationMethod} 读取摘要并完成验证。
 *
 * <p>当前类型不能用于 HTTP 响应或日志，避免不可逆摘要离开 Password 模块。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class PasswordCredential {

    /** Password 凭据的有限生命周期状态。 */
    public enum Status implements BaseEnum {
        /** 当前允许密码认证。 */
        ACTIVE,
        /** 当前密码已暂停，必须重新设置密码。 */
        RESET_REQUIRED,
        /** 凭据已停用，通常与账号删除的逻辑删除同时出现。 */
        DISABLED;

        @Override
        public @NotNull String value() {
            return name();
        }

        @Override
        public @NotNull String description() {
            return switch (this) {
                case ACTIVE -> "有效";
                case RESET_REQUIRED -> "需要重置";
                case DISABLED -> "已停用";
            };
        }
    }

    private final Long accountId;

    private final String passwordHash;

    private final Version version;

    private final @NotNull Instant updatedAt;

    private final @NotNull Status status;

    /**
     * 创建账号的密码凭据。
     *
     * @param accountId 密码凭据所属账号
     * @param passwordHash Argon2id 不可逆摘要
     * @param updatedAt 凭据最近更新时间
     * @param version 凭据版本
     * @param status 凭据生命周期状态
     */
    @AutomapConstructor
    public PasswordCredential(
            @NotNull Long accountId,
            @NotNull String passwordHash,
            @NotNull Instant updatedAt,
            @NotNull Version version,
            @NotNull Status status) {
        this.accountId = Objects.requireNonNull(accountId, "密码凭据账号不能为空");
        this.passwordHash = Objects.requireNonNull(passwordHash, "密码摘要不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "密码凭据更新时间不能为空");
        this.version = Objects.requireNonNull(version, "密码凭据版本不能为空");
        this.status = Objects.requireNonNull(status, "密码凭据状态不能为空");
    }

    /** 创建状态为 ACTIVE 的密码凭据，供现有创建调用方使用。 */
    public PasswordCredential(
            @NotNull Long accountId,
            @NotNull String passwordHash,
            @NotNull Instant updatedAt,
            @NotNull Version version) {
        this(accountId, passwordHash, updatedAt, version, ACTIVE);
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

    /** @return 密码凭据生命周期状态 */
    public @NotNull Status status() {
        return status;
    }

    /** @return 当前凭据允许密码认证时为 {@code true} */
    public boolean active() {
        return status == ACTIVE;
    }
}
