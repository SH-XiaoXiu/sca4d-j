package com.sca4d.app.server.authentication.view;

import com.sca4d.auth.totp.enrollment.TotpEnrollment;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 不包含 Secret、URI 或二维码内容的 TOTP enrollment。 */
public class TotpEnrollmentView {

    private final Instant expiresAt;

    private final String enrollmentId;

    private TotpEnrollmentView(String enrollmentId, Instant expiresAt) {
        this.enrollmentId = enrollmentId;
        this.expiresAt = expiresAt;
    }

    /**
     * 创建 enrollment 安全投影。
     *
     * @param enrollment TOTP enrollment 元数据
     * @return enrollment 安全投影
     */
    public static @NotNull TotpEnrollmentView from(@NotNull TotpEnrollment enrollment) {
        TotpEnrollment checked = Objects.requireNonNull(enrollment, "TOTP enrollment 不能为空");
        return new TotpEnrollmentView(checked.enrollmentId(), checked.expiresAt());
    }

    /**
     * 返回 enrollment 标识。
     *
     * @return enrollment 标识
     */
    @SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
    public @NotNull String getEnrollmentId() {
        return enrollmentId;
    }

    /**
     * 返回 enrollment 过期时间。
     *
     * @return enrollment 过期时间
     */
    @SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
    public @NotNull Instant getExpiresAt() {
        return expiresAt;
    }
}
