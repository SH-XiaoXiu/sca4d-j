package com.sca4d.core.api.job;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;

/** 声明 Job 单个节点的最大尝试次数和固定重试间隔。 */
public final class JobRetryPolicy {

    private final int maxAttempts;

    private final @NotNull Duration delay;

    /**
     * 创建重试策略。
     *
     * @param maxAttempts 包含首次执行的总尝试次数，至少为 1
     * @param delay 两次尝试之间的非负间隔
     * @throws NullPointerException 重试间隔为空时抛出
     * @throws IllegalArgumentException 最大尝试次数小于 1 或间隔为负数时抛出
     */
    public JobRetryPolicy(int maxAttempts, @NotNull Duration delay) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Job 最大尝试次数不能小于 1");
        }
        Duration checked = Objects.requireNonNull(delay, "Job 重试间隔不能为空");
        if (checked.isNegative()) {
            throw new IllegalArgumentException("Job 重试间隔不能为负数");
        }
        this.maxAttempts = maxAttempts;
        this.delay = checked;
    }

    /**
     * 创建不执行 Job 层重试的策略。
     *
     * @return 最大尝试次数为 1、间隔为零的策略
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull JobRetryPolicy once() {
        return new JobRetryPolicy(1, Duration.ZERO);
    }

    /**
     * 返回最大尝试次数。
     *
     * @return 包含首次执行的总尝试次数
     */
    @Contract(pure = true)
    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * 返回失败后的固定等待间隔。
     *
     * @return 两次尝试之间的非负间隔
     */
    @Contract(pure = true)
    public @NotNull Duration delay() {
        return delay;
    }
}
