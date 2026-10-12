package com.sca4d.core.api.retry;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** 表示等待后再次尝试或停止，两个结果不能同时存在。 */
public class RetryDecision {
    private final @Nullable Duration delay;

    private final @Nullable RetryStopReason reason;

    private RetryDecision(@Nullable Duration delay, @Nullable RetryStopReason reason) {
        this.delay = delay;
        this.reason = reason;
    }

    /**
     * @param delay 非负整数毫秒间隔
     * @return 继续尝试的决定
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull RetryDecision retryAfter(@NotNull Duration delay) {
        Backoff.millis(delay);
        return new RetryDecision(delay, null);
    }

    /**
     * @param reason 稳定停止原因
     * @return 停止尝试的决定
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull RetryDecision stop(@NotNull RetryStopReason reason) {
        return new RetryDecision(null, Objects.requireNonNull(reason));
    }

    /** @return 是否允许等待后再次尝试 */
    public boolean retrying() {
        return delay != null;
    }

    /** @return 继续时的等待时长；停止时为空 */
    public @NotNull Optional<Duration> retryDelay() {
        return Optional.ofNullable(delay);
    }

    /** @return 停止时的原因；继续时为空 */
    public @NotNull Optional<RetryStopReason> stopReason() {
        return Optional.ofNullable(reason);
    }
}
