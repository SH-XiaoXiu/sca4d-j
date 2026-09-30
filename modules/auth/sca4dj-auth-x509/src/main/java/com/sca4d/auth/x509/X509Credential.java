package com.sca4d.auth.x509;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.enums.BaseEnum;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 Account 登记的 X.509 公钥证书身份、有效期、状态和乐观版本。 */
public class X509Credential {

    /** 公钥证书凭据的有限生命周期状态。 */
    public enum Status implements BaseEnum {
        /** 当前允许认证。 */
        ACTIVE,
        /** 已撤销认证资格。 */
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

    private final @NotNull Instant notAfter;

    private final @NotNull Instant notBefore;

    private final @NotNull Instant updatedAt;

    private final @NotNull Status status;

    private final @NotNull String fingerprintSha256;

    private final @NotNull String serialNumber;

    private final @NotNull String subjectDn;

    private final @NotNull Version version;

    private final long accountId;

    private final long id;

    /** 创建从数据库读取的 X.509 Credential。 */
    public X509Credential(
            long id,
            long accountId,
            @NotNull String fingerprintSha256,
            @NotNull String serialNumber,
            @NotNull String subjectDn,
            @NotNull Instant notBefore,
            @NotNull Instant notAfter,
            @NotNull Status status,
            @NotNull Version version,
            @NotNull Instant updatedAt) {
        if (id <= 0 || accountId <= 0) {
            throw new IllegalArgumentException("X.509 Credential 和 Account 标识必须为正数");
        }
        this.id = id;
        this.accountId = accountId;
        this.fingerprintSha256 = TextUtils.requireText(fingerprintSha256, "证书指纹");
        this.serialNumber = TextUtils.requireText(serialNumber, "证书序列号");
        this.subjectDn = TextUtils.requireText(subjectDn, "证书 Subject DN");
        this.notBefore = Objects.requireNonNull(notBefore, "证书生效时间不能为空");
        this.notAfter = Objects.requireNonNull(notAfter, "证书失效时间不能为空");
        this.status = Objects.requireNonNull(status, "证书状态不能为空");
        if (this.fingerprintSha256.length() != 64
                || this.fingerprintSha256.chars()
                        .anyMatch(
                                value -> !Character.isDigit(value)
                                        && (value < 'a' || value > 'f'))) {
            throw new IllegalArgumentException("证书指纹必须是 SHA-256 小写十六进制文本");
        }
        this.version = Objects.requireNonNull(version, "证书版本不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "证书更新时间不能为空");
        if (!notAfter.isAfter(notBefore)) {
            throw new IllegalArgumentException("证书失效时间必须晚于生效时间");
        }
    }

    /** @return X.509 Credential 标识 */
    public long id() {
        return id;
    }

    /** @return 凭据所属 Account */
    public long accountId() {
        return accountId;
    }

    /** @return DER 证书的小写 SHA-256 十六进制指纹 */
    public @NotNull String fingerprintSha256() {
        return fingerprintSha256;
    }

    /** @return X.509 序列号十六进制文本 */
    public @NotNull String serialNumber() {
        return serialNumber;
    }

    /** @return X.509 Subject DN */
    public @NotNull String subjectDn() {
        return subjectDn;
    }

    /** @return 证书生效时间 */
    public @NotNull Instant notBefore() {
        return notBefore;
    }

    /** @return 证书失效时间 */
    public @NotNull Instant notAfter() {
        return notAfter;
    }

    /** @return 数据库状态仍允许认证时为 `true` */
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

    /** @return 最近登记或撤销时间 */
    public @NotNull Instant updatedAt() {
        return updatedAt;
    }

    /** @return 指定时间处于证书有效期且凭据仍有效时为 `true` */
    public boolean usableAt(@NotNull Instant instant) {
        Instant checked = Objects.requireNonNull(instant, "证书校验时间不能为空");
        return active() && !checked.isBefore(notBefore) && checked.isBefore(notAfter);
    }
}
