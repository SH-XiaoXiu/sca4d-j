package com.sca4d.core.api.authentication;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 认证尝试的公开进度，不包含账号、Proof 或认证证据。
 */
@SuppressWarnings("ClassCanBeRecord")
public class AuthenticationProgress {

    private final AuthenticationAttemptId attemptId;

    private final int remainingFactorCount;

    private final List<TreeCode> availableMethods;

    private final Instant expiresAt;

    /**
     * 创建不暴露账号和认证材料的尝试进度。
     *
     * @param attemptId            尝试标识
     * @param remainingFactorCount 仍需补充的不同认证因素数量
     * @param availableMethods     当前允许提交的方法
     * @param expiresAt            尝试过期时间
     * @throws IllegalArgumentException 状态、剩余因素数量和可用方法不一致时抛出
     */
    public AuthenticationProgress(
            @NotNull AuthenticationAttemptId attemptId,
            int remainingFactorCount,
            @NotNull List<TreeCode> availableMethods,
            @NotNull Instant expiresAt) {
        this.attemptId = Objects.requireNonNull(attemptId, "认证尝试标识不能为空");
        this.availableMethods = List.copyOf(Objects.requireNonNull(availableMethods, "可用认证方法不能为空"));
        this.expiresAt = Objects.requireNonNull(expiresAt, "认证尝试过期时间不能为空");
        if ((remainingFactorCount == 0 && !this.availableMethods.isEmpty())
                || (remainingFactorCount > 0 && this.availableMethods.isEmpty())
                || remainingFactorCount < 0) {
            throw new IllegalArgumentException("剩余因素数量和可用认证方法不一致");
        }
        this.remainingFactorCount = remainingFactorCount;
    }

    /**
     * 返回调用方继续提交因素或消费结果时必须回传的标识。
     */
    public @NotNull AuthenticationAttemptId attemptId() {
        return attemptId;
    }

    /**
     * 返回本次尝试是否已经满足认证策略，可以单次消费结果。
     */
    public boolean satisfied() {
        return remainingFactorCount == 0;
    }

    /**
     * 返回满足要求前还需成功验证的不同因素数量。
     */
    public int remainingFactorCount() {
        return remainingFactorCount;
    }

    /**
     * 返回下一次提交可以选择的认证方法。
     */
    public @NotNull @Unmodifiable List<TreeCode> availableMethods() {
        return availableMethods;
    }

    /**
     * 返回认证引擎不再接受提交或消费请求的时刻。
     */
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }
}
