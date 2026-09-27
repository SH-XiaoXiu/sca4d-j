package com.sca4d.auth.totp.credential.mapper;

import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 MyBatis 从 TOTP 凭据表读取的模块内部持久化状态。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class TotpCredentialState {

    private final Long accountId;

    private final String secretKeyId;

    private final byte[] secretCiphertext;

    private final long lastAcceptedStep;

    private final Version version;

    /**
     * 创建 TOTP 凭据的持久化状态。
     *
     * @param accountId 凭据所属账号
     * @param secretKeyId 加密密钥标识
     * @param secretCiphertext TOTP 共享密钥密文
     * @param lastAcceptedStep 最近一次接受的 TOTP 时间步
     * @param version 凭据版本
     */
    public TotpCredentialState(
            @NotNull Long accountId,
            @NotNull String secretKeyId,
            byte @NotNull [] secretCiphertext,
            long lastAcceptedStep,
            @NotNull Version version) {
        this.accountId = Objects.requireNonNull(accountId, "TOTP 凭据账号不能为空");
        this.secretKeyId = Objects.requireNonNull(secretKeyId, "TOTP 密钥标识不能为空");
        this.secretCiphertext = Objects.requireNonNull(secretCiphertext, "TOTP 密文不能为空").clone();
        this.lastAcceptedStep = lastAcceptedStep;
        this.version = Objects.requireNonNull(version, "TOTP 凭据版本不能为空");
    }

    /**
     * 返回 Account 标识。
     *
     * @return Account 标识
     */
    public @NotNull Long accountId() {
        return accountId;
    }

    /**
     * 返回加密密钥标识。
     *
     * @return Secret key-id
     */
    public @NotNull String secretKeyId() {
        return secretKeyId;
    }

    /**
     * 返回 TOTP 共享密钥密文的防御性副本。
     *
     * @return 密文副本
     */
    public byte @NotNull [] secretCiphertext() {
        return secretCiphertext.clone();
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
