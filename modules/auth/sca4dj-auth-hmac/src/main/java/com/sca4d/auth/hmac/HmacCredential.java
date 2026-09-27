package com.sca4d.auth.hmac;

import static com.sca4d.auth.hmac.HmacCredential.Status.ACTIVE;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.enums.BaseEnum;
import com.sca4d.core.api.security.EncryptedSecret;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 Account 当前 MAC Key 的加密值、状态和乐观版本。 */
public final class HmacCredential {

    /** MAC 凭据的有限生命周期状态。 */
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

    private final @NotNull EncryptedSecret encryptedKey;

    private final @NotNull Status status;

    private final @NotNull Version version;

    private final @NotNull Instant updatedAt;

    /** 创建从数据库读取的 MAC Credential。 */
    public HmacCredential(
            long accountId,
            @NotNull String keyId,
            byte @NotNull [] ciphertext,
            @NotNull Status status,
            @NotNull Version version,
            @NotNull Instant updatedAt) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        this.accountId = accountId;
        this.encryptedKey = new EncryptedSecret(keyId, ciphertext);
        this.status = Objects.requireNonNull(status, "HMAC Credential 状态不能为空");
        this.version = Objects.requireNonNull(version, "HMAC Credential 版本不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "HMAC Credential 更新时间不能为空");
    }

    /** @return 凭据所属 Account */
    public long accountId() {
        return accountId;
    }

    /** @return 当前 Credential 是否允许认证 */
    public boolean active() {
        return status == ACTIVE;
    }

    /** @return 当前凭据状态 */
    public @NotNull Status status() {
        return status;
    }

    /** @return 当前乐观版本 */
    public @NotNull Version version() {
        return version;
    }

    /** @return 最近签发、轮换或撤销时间 */
    public @NotNull Instant updatedAt() {
        return updatedAt;
    }

    /** @return 仅供 MAC 模块解密验签的密文 */
    @ApiStatus.Internal
    public @NotNull EncryptedSecret encryptedKey() {
        return encryptedKey;
    }
}
