package com.sca4d.app.server.authentication.view;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.sca4d.core.api.authentication.AuthenticationProgress;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 向 HTTP 暴露敏感 verification 的标识、状态、可用因素和过期时间。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public final class VerificationView {

    private final String verificationId;

    private final String status;

    private final int remainingFactorCount;

    private final List<String> availableMethods;

    private final Instant expiresAt;

    private VerificationView(
            String verificationId,
            String status,
            int remainingFactorCount,
            List<String> availableMethods,
            Instant expiresAt) {
        this.verificationId = verificationId;
        this.status = status;
        this.remainingFactorCount = remainingFactorCount;
        this.availableMethods = List.copyOf(availableMethods);
        this.expiresAt = expiresAt;
    }

    /**
     * 创建认证引擎进度的 verification HTTP 投影。
     *
     * @param result 认证引擎进度
     * @return 不暴露内部 attempt 命名的 verification 投影
     */
    public static @NotNull VerificationView from(@NotNull AuthenticationProgress result) {
        AuthenticationProgress checked = Objects.requireNonNull(result, "认证进度不能为空");
        return new VerificationView(
                checked.attemptId().value(),
                checked.satisfied() ? "SATISFIED" : "FACTOR_REQUIRED",
                checked.remainingFactorCount(),
                checked.availableMethods().stream().map(code -> code.format(SLASH)).toList(),
                checked.expiresAt());
    }

    /**
     * 返回 verification 的不透明标识。
     *
     * @return verification 的不透明标识
     */
    public @NotNull String getVerificationId() {
        return verificationId;
    }

    /**
     * 返回 verification 状态。
     *
     * @return {@code FACTOR_REQUIRED} 或 {@code SATISFIED}
     */
    public @NotNull String getStatus() {
        return status;
    }

    /**
     * 返回尚需提交的独立因素数。
     *
     * @return 尚需提交的独立因素数
     */
    public int getRemainingFactorCount() {
        return remainingFactorCount;
    }

    /**
     * 返回当前可提交的认证方法编码。
     *
     * @return 当前可提交的认证方法编码
     */
    public @NotNull List<String> getAvailableMethods() {
        return availableMethods;
    }

    /**
     * 返回 verification 过期时间。
     *
     * @return verification 过期时间
     */
    public @NotNull Instant getExpiresAt() {
        return expiresAt;
    }
}
