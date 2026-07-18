package com.sca4d.common.messaging;

import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * 消息发布重试策略。
 */
@Data
@Accessors(chain = true)
public class MessageRetryPolicy {

    private int maxAttempts = 10;
    private Duration initialBackoff = Duration.ofSeconds(5);
    private Duration maxBackoff = Duration.ofMinutes(5);

    /**
     * 计算下一次重试退避时间。
     *
     * @param nextAttempt 即将写入的尝试次数
     * @return 退避时间
     */
    public @NotNull Duration backoff(int nextAttempt) {
        long multiplier = 1L << Math.clamp(nextAttempt - 1, 0, 20);
        Duration calculated = initialBackoff.multipliedBy(multiplier);
        return calculated.compareTo(maxBackoff) > 0 ? maxBackoff : calculated;
    }
}
