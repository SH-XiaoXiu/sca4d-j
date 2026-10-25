package com.sca4d.core.api.retry;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * 保存包含首次执行的次数限制和不可变退避规则，不持有执行进度。
 */
public class RetryRule {
    private final Backoff backoff;

    private final long maximumAttempts;

    private RetryRule(long maximumAttempts, Backoff backoff) {
        this.maximumAttempts = maximumAttempts;
        this.backoff = Objects.requireNonNull(backoff, "重试退避不能为空");
    }

    /**
     * @return 只执行首次操作的规则
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull RetryRule once() {
        return atMost(1, Backoff.fixed(Duration.ZERO));
    }

    /**
     * @param maximumAttempts 包含首次执行的正数上限
     * @param backoff 失败后的退避
     * @return 有限重试规则
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull RetryRule atMost(long maximumAttempts, @NotNull Backoff backoff) {
        if (maximumAttempts < 1)
            throw new IllegalArgumentException("最大尝试次数必须为正数");
        return new RetryRule(maximumAttempts, backoff);
    }

    /**
     * @param backoff 每次均为正数等待的退避
     * @return 不限制业务尝试次数的规则
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull RetryRule unlimited(@NotNull Backoff backoff) {
        Objects.requireNonNull(backoff, "重试退避不能为空");
        if (backoff.delays().stream().anyMatch(Duration::isZero)) {
            throw new IllegalArgumentException("无限重试不能使用零间隔");
        }
        return new RetryRule(0, backoff);
    }

    /**
     * @return 包含首次执行的上限；空表示不限制业务次数
     */
    public @NotNull OptionalLong maximumAttempts() {
        return maximumAttempts == 0 ? OptionalLong.empty() : OptionalLong.of(maximumAttempts);
    }

    /** @return 失败后计算等待时长的规则 */
    public @NotNull Backoff backoff() {
        return backoff;
    }

    /**
     * @return 规则只允许首次执行、不进行任何重试时为 true
     */
    public boolean isOnce() {
        return maximumAttempts == 1;
    }
}
