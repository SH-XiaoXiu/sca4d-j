package com.sca4d.auth.totp.enrollment;

import com.sca4d.auth.totp.TotpErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.security.EncryptedSecret;

import java.time.Instant;
import java.util.Objects;

/** Redis 保存的 TOTP enrollment 加密状态。 */
@SuppressWarnings("ClassCanBeRecord") // 状态对象沿用普通 class，便于显式表达状态转换。
class TotpEnrollmentState {

    private final EncryptedSecret secret;

    private final Instant expiresAt;

    private final Long accountId;

    private final String id;

    private final TotpEnrollmentStatus status;

    private final long version;

    TotpEnrollmentState(
            String id,
            Long accountId,
            EncryptedSecret secret,
            Instant expiresAt,
            TotpEnrollmentStatus status,
            long version) {
        this.id = Objects.requireNonNull(id, "TOTP enrollment 标识不能为空");
        this.accountId = Objects.requireNonNull(accountId, "TOTP enrollment 账号不能为空");
        this.secret = Objects.requireNonNull(secret, "TOTP enrollment 密钥不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "TOTP enrollment 过期时间不能为空");
        this.status = Objects.requireNonNull(status, "TOTP enrollment 状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("TOTP enrollment 版本不能为负数");
        }
        this.version = version;
    }

    String id() {
        return id;
    }

    Long accountId() {
        return accountId;
    }

    EncryptedSecret secret() {
        return secret;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    TotpEnrollmentStatus status() {
        return status;
    }

    long version() {
        return version;
    }

    TotpEnrollmentState consume() {
        if (status != TotpEnrollmentStatus.PENDING) {
            throw new BusinessException(TotpErrorCode.ENROLLMENT_CONSUMED);
        }
        return new TotpEnrollmentState(
                id,
                accountId,
                secret,
                expiresAt,
                TotpEnrollmentStatus.CONSUMED,
                Math.incrementExact(version));
    }

    TotpEnrollment enrollment() {
        return new TotpEnrollment(id, accountId, expiresAt);
    }
}
