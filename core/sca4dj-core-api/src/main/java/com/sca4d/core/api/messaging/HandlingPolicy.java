package com.sca4d.core.api.messaging;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;

/** 限制 MessageHandler 因技术失败再次处理同一 RabbitMQ 消息的次数和间隔。 */
public final class HandlingPolicy {

    private final int maximumAttempts;

    private final @NotNull Duration retryDelay;

    public HandlingPolicy(int maximumAttempts, @NotNull Duration retryDelay) {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("消息处理最大尝试次数必须为正数");
        }
        Duration checkedDelay = Objects.requireNonNull(retryDelay, "消息处理重试间隔不能为空");
        if (checkedDelay.isNegative()) {
            throw new IllegalArgumentException("消息处理重试间隔不能为负数");
        }
        this.maximumAttempts = maximumAttempts;
        this.retryDelay = checkedDelay;
    }

    @CheckReturnValue
    public static @NotNull HandlingPolicy noRetry() {
        return new HandlingPolicy(1, Duration.ZERO);
    }

    public int maximumAttempts() {
        return maximumAttempts;
    }

    public @NotNull Duration retryDelay() {
        return retryDelay;
    }
}
