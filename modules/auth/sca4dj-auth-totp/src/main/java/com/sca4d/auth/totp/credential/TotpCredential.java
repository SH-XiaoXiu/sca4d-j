package com.sca4d.auth.totp.credential;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.security.EncryptedSecret;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Account 当前激活的加密 TOTP 凭据及已接受时间步下限。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class TotpCredential {

    private final Long accountId;

    private final EncryptedSecret secret;

    private final long lastAcceptedStep;

    private final Version version;

    /**
     * 创建 TOTP 凭据。
     *
     * @param accountId 凭据所属账号
     * @param secret 加密后的 TOTP 共享密钥
     * @param lastAcceptedStep 最近一次接受的 TOTP 时间步
     * @param version 凭据版本
     */
    public TotpCredential(
            @NotNull Long accountId,
            @NotNull EncryptedSecret secret,
            long lastAcceptedStep,
            @NotNull Version version) {
        this.accountId = Objects.requireNonNull(accountId, "TOTP 凭据账号不能为空");
        this.secret = Objects.requireNonNull(secret, "TOTP 加密密钥不能为空");
        this.lastAcceptedStep = lastAcceptedStep;
        this.version = Objects.requireNonNull(version, "TOTP 凭据版本不能为空");
    }

    /**
     * 返回凭据所属账号。
     *
     * @return 凭据所属账号
     */
    public @NotNull Long accountId() {
        return accountId;
    }

    /** 仅供 TOTP 认证和 enrollment 生命周期解密，不得返回给 Controller 或日志。 */
    @ApiStatus.Internal
    @NotNull
    public EncryptedSecret secret() {
        return secret;
    }

    /**
     * 返回已接受的 TOTP 时间步下限。
     *
     * @return 最近一次接受的 TOTP 时间步
     */
    public long lastAcceptedStep() {
        return lastAcceptedStep;
    }

    /**
     * 返回凭据版本。
     *
     * @return 凭据版本
     */
    public @NotNull Version version() {
        return version;
    }
}
