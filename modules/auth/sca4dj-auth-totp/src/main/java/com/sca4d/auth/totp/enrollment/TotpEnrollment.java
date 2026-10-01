package com.sca4d.auth.totp.enrollment;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回给调用方的非秘密 TOTP enrollment 元数据。 */
@SuppressWarnings("ClassCanBeRecord") // 保持与项目值对象一致的显式构造校验和访问器风格。
public class TotpEnrollment {

    private final Instant expiresAt;

    private final Long accountId;

    private final String enrollmentId;

    /**
     * 创建 TOTP enrollment 元数据。
     *
     * @param enrollmentId enrollment 不透明标识
     * @param accountId enrollment 所属账号
     * @param expiresAt enrollment 过期时间
     */
    public TotpEnrollment(
            @NotNull String enrollmentId,
            @NotNull Long accountId,
            @NotNull Instant expiresAt) {
        this.enrollmentId = Objects.requireNonNull(enrollmentId, "TOTP enrollment 标识不能为空");
        this.accountId = Objects.requireNonNull(accountId, "TOTP enrollment 账号不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "TOTP enrollment 过期时间不能为空");
    }

    /**
     * enrollment 不透明标识。
     *
     * @return enrollment 不透明标识
     */
    public @NotNull String enrollmentId() {
        return enrollmentId;
    }

    /**
     * enrollment 所属账号。
     *
     * @return enrollment 所属账号
     */
    public @NotNull Long accountId() {
        return accountId;
    }

    /**
     * enrollment 过期时间。
     *
     * @return enrollment 过期时间
     */
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }
}
