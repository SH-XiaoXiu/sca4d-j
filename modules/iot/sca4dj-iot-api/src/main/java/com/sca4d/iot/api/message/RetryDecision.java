package com.sca4d.iot.api.message;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 表示 retry 策略要求停止或在明确时刻再次发送。 */
public class RetryDecision {

    private final @Nullable Instant retryAt;

    private final @Nullable IotFailureCode stopFailure;

    private RetryDecision(@Nullable Instant retryAt, @Nullable IotFailureCode stopFailure) {
        this.retryAt = retryAt;
        this.stopFailure = stopFailure;
    }

    /** @param retryAt 下一次允许尝试的时刻 @return 重试结果 */
    @CheckReturnValue
    public static @NotNull RetryDecision retryAt(@NotNull Instant retryAt) {
        return new RetryDecision(retryAt, null);
    }

    /** @param failure 停止重试的稳定失败 code @return 停止结果 */
    @CheckReturnValue
    public static @NotNull RetryDecision stop(@NotNull IotFailureCode failure) {
        return new RetryDecision(null, Objects.requireNonNull(failure, "停止重试失败 code 不能为空"));
    }

    /** @return 下一次尝试时间；停止时为空 */
    public @NotNull Optional<Instant> retryAt() {
        return Optional.ofNullable(retryAt);
    }

    /** @return 停止重试的稳定失败 code；继续重试时为空 */
    public @NotNull Optional<IotFailureCode> stopFailure() {
        return Optional.ofNullable(stopFailure);
    }
}
