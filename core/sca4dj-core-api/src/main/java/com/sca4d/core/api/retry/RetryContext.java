package com.sca4d.core.api.retry;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 保存一次失败发生时的累计尝试次数和类型化失败事实。
 */
public class RetryContext<F> {
    private final F failure;

    private final long attempt;

    private RetryContext(long attempt, F failure) {
        if (attempt < 1)
            throw new IllegalArgumentException("失败尝试次数必须为正数");
        this.attempt = attempt;
        this.failure = Objects.requireNonNull(failure, "失败事实不能为空");
    }

    /**
     * @param attempt 包含本次失败的累计尝试次数
     * @param failure 本次失败事实
     * @param <F> 失败类型
     * @return 不可变失败上下文
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static <F> @NotNull RetryContext<F> failed(long attempt, @NotNull F failure) {
        return new RetryContext<>(attempt, failure);
    }

    /** @return 包含当前失败的累计尝试次数，从 1 开始 */
    public long attempt() {
        return attempt;
    }

    /** @return 本次失败的类型化事实 */
    public @NotNull F failure() {
        return failure;
    }
}
