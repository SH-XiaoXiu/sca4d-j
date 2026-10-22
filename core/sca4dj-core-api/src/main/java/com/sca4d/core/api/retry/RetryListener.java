package com.sca4d.core.api.retry;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * 观察重试执行，不参与重试决定；监听异常不能改变操作结果。
 */
public interface RetryListener {
    /**
     * @param context 已经发生的失败
     * @param decision 本次失败唯一的重试决定
     */
    default void failed(@NotNull RetryContext<Throwable> context, @NotNull RetryDecision decision) {
    }

    /**
     * @param context 即将等待的失败
     * @param delay 等待时长
     */
    default void waiting(@NotNull RetryContext<Throwable> context, @NotNull Duration delay) {
    }

    /**
     * @param context 已结束等待的失败，包含等待被中断的情况
     */
    default void waited(@NotNull RetryContext<Throwable> context) {
    }

    /**
     * @param attempt 成功的尝试序号
     */
    default void succeeded(long attempt) {
    }
}
