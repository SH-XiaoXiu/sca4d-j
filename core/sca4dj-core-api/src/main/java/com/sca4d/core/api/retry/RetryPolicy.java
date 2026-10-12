package com.sca4d.core.api.retry;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 根据失败事实和规则作出无副作用的决定；实现必须可并发使用且不保存执行进度。
 */
@FunctionalInterface
public interface RetryPolicy<F> {
    /**
     * @param context 当前失败事实
     * @return 继续或停止决定，不执行等待和外部操作
     */
    @CheckReturnValue
    @Contract(pure = true)
    @NotNull
    RetryDecision evaluate(@NotNull RetryContext<? extends F> context);

    /**
     * @param rule 次数和退避规则
     * @param <F> 失败类型
     * @return 对已经分类的失败应用规则的策略
     */
    @CheckReturnValue
    @Contract(pure = true)
    static <F> @NotNull RetryPolicy<F> always(@NotNull RetryRule rule) {
        return forFailures(rule, failure -> true);
    }

    /**
     * @param rule 次数和退避规则
     * @param retryable 无副作用的失败判断
     * @param <F> 失败类型
     * @return 先筛选失败再检查次数的策略
     */
    @CheckReturnValue
    @Contract(pure = true)
    static <F> @NotNull RetryPolicy<F> forFailures(@NotNull RetryRule rule, @NotNull Predicate<? super F> retryable) {
        Objects.requireNonNull(rule, "重试规则不能为空");
        Objects.requireNonNull(retryable, "失败判断不能为空");
        return context -> {
            Objects.requireNonNull(context, "重试上下文不能为空");
            if (rule.isOnce())
                return RetryDecision.stop(RetryStopReason.DISABLED);
            if (!retryable.test(context.failure()))
                return RetryDecision.stop(RetryStopReason.NOT_RETRYABLE);
            if (rule.maximumAttempts().isPresent() && context.attempt() >= rule.maximumAttempts().getAsLong()) {
                return RetryDecision.stop(RetryStopReason.ATTEMPTS_EXHAUSTED);
            }
            if (context.attempt() == Long.MAX_VALUE)
                throw new ArithmeticException("重试计数超过 long 表示范围");
            return RetryDecision.retryAfter(rule.backoff().delayAfter(context.attempt()));
        };
    }
}
