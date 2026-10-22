package com.sca4d.core.api.retry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 执行同步操作并按共同策略重试，不传播或创建 Call、事务和分布式状态。
 */
public interface RetryExecutor {
    /**
     * @param policy 异常判断策略
     * @param operation 单次操作
     * @param <T> 结果类型
     * @param <E> 操作异常类型
     * @return 操作结果
     * @throws E 最后一次操作异常
     */
    default <T, E extends Exception> @Nullable T execute(
            @NotNull RetryPolicy<Throwable> policy,
            @NotNull RetryOperation<T, E> operation) throws E {
        return execute(policy, operation, new RetryListener() {
        });
    }

    /**
     * @param policy 异常判断策略
     * @param operation 单次操作
     * @param listener 只读监听器
     * @param <T> 结果类型
     * @param <E> 操作异常类型
     * @return 操作结果
     * @throws E 最后一次操作异常
     */
    <T, E extends Exception> @Nullable T execute(
            @NotNull RetryPolicy<Throwable> policy,
            @NotNull RetryOperation<T, E> operation,
            @NotNull RetryListener listener) throws E;
}
