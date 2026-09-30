package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.utils.DurationUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 保存一次限流额度判断是否允许以及拒绝后的等待时间。 */
public class RateLimitDecision {

    private static final RateLimitDecision ALLOWED = new RateLimitDecision(true, Duration.ZERO);

    private final @NotNull Duration retryAfter;

    private final boolean allowed;

    private RateLimitDecision(boolean allowed, @NotNull Duration retryAfter) {
        this.allowed = allowed;
        this.retryAfter = retryAfter;
    }

    /**
     * 允许当前调用的结果。
     *
     * @return 不要求等待的允许结果
     */
    @Contract(pure = true)
    public static @NotNull RateLimitDecision allow() {
        return ALLOWED;
    }

    /**
     * 返回拒绝当前调用的结果。
     *
     * @param retryAfter 再次取得额度前至少需要等待的时间
     * @return 携带正数等待时间的拒绝结果
     */
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull RateLimitDecision deny(@NotNull Duration retryAfter) {
        return new RateLimitDecision(
                false,
                DurationUtils.requirePositive(retryAfter, "限流重试等待时间"));
    }

    /**
     * 当前调用是否取得全部额度。
     *
     * @return 允许调用时返回 `true`
     */
    @Contract(pure = true)
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * 拒绝后的等待时间。
     *
     * @return 拒绝时为正数等待时间，允许时为零
     */
    @Contract(pure = true)
    public @NotNull Duration retryAfter() {
        return retryAfter;
    }
}
