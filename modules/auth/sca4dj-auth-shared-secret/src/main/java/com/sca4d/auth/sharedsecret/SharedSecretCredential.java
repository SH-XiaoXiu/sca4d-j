package com.sca4d.auth.sharedsecret;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.enums.BaseEnum;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存一个 Account 当前 Shared Secret 的不可逆摘要、状态和乐观版本。 */
public final class SharedSecretCredential {

    /** Shared Secret 凭据的有限生命周期状态。 */
    public enum Status implements BaseEnum {
        /** 当前允许认证。 */
        ACTIVE,
        /** 已撤销认证材料。 */
        REVOKED;

        @Override
        public @NotNull String value() {
            return name();
        }

        @Override
        public @NotNull String description() {
            return this == ACTIVE ? "有效" : "已撤销";
        }
    }

    private final long accountId;

    private final @NotNull String secretHash;

    private final @NotNull Status status;

    private final @NotNull Version version;

    private final @NotNull Instant updatedAt;

    /** 创建从数据库读取的 Shared Secret 凭据。 */
    public SharedSecretCredential(
            long accountId,
            @NotNull String secretHash,
            @NotNull Status status,
            @NotNull Version version,
            @NotNull Instant updatedAt) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        this.accountId = accountId;
        this.secretHash = TextUtils.requireText(secretHash, "Shared Secret 摘要");
        this.status = Objects.requireNonNull(status, "Shared Secret 状态不能为空");
        this.version = Objects.requireNonNull(version, "Shared Secret 版本不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Shared Secret 更新时间不能为空");
    }

    /** @return 凭据所属 Account */
    public long accountId() {
        return accountId;
    }

    /** @return 当前凭据是否允许认证 */
    public boolean active() {
        return status == Status.ACTIVE;
    }

    /** @return 当前凭据状态 */
    public @NotNull Status status() {
        return status;
    }

    /** @return 当前乐观版本 */
    public @NotNull Version version() {
        return version;
    }

    /** @return 凭据最近创建、轮换或撤销时间 */
    public @NotNull Instant updatedAt() {
        return updatedAt;
    }

    /** @return 仅供认证方法校验且不得写入响应或日志的摘要 */
    @ApiStatus.Internal
    public @NotNull String secretHash() {
        return secretHash;
    }
}
