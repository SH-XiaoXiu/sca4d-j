package com.sca4d.app.server.authentication.view;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.sca4d.core.api.authentication.AuthenticationProgress;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 不包含 Account、Proof 或 Evidence 的动作认证进度。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class AuthenticationProgressView {

    private final Instant expiresAt;

    private final List<String> availableMethods;

    private final String attemptId;

    private final String status;

    private final int remainingFactorCount;

    private AuthenticationProgressView(
            String attemptId,
            String status,
            int remainingFactorCount,
            List<String> availableMethods,
            Instant expiresAt) {
        this.attemptId = attemptId;
        this.status = status;
        this.remainingFactorCount = remainingFactorCount;
        this.availableMethods = List.copyOf(availableMethods);
        this.expiresAt = expiresAt;
    }

    /** 创建认证引擎进度的 HTTP 投影。 */
    public static @NotNull AuthenticationProgressView from(@NotNull AuthenticationProgress progress) {
        AuthenticationProgress checked = Objects.requireNonNull(progress, "认证进度不能为空");
        return new AuthenticationProgressView(
                checked.attemptId().value(),
                checked.satisfied() ? "SATISFIED" : "FACTOR_REQUIRED",
                checked.remainingFactorCount(),
                checked.availableMethods().stream().map(code -> code.format(SLASH)).toList(),
                checked.expiresAt());
    }

    public @NotNull String getAttemptId() {
        return attemptId;
    }

    public @NotNull String getStatus() {
        return status;
    }

    public int getRemainingFactorCount() {
        return remainingFactorCount;
    }

    public @NotNull List<String> getAvailableMethods() {
        return availableMethods;
    }

    public @NotNull Instant getExpiresAt() {
        return expiresAt;
    }
}
