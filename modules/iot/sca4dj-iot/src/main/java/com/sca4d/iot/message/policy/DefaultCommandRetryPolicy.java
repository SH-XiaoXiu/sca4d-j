package com.sca4d.iot.message.policy;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.api.message.RetryDecision;
import com.sca4d.iot.api.message.RetryPolicy;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

/** 使用固定退避并在第五次发送仍失败后停止的默认命令重试策略。 */
public final class DefaultCommandRetryPolicy implements RetryPolicy {

    public static final @NotNull TreeCode CODE = TreeCode.of("IOT/DOWNLINK/RETRY/COMMAND/DEFAULT");

    private static final Duration[] RETRY_DELAYS = {
            Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2)
    };

    /** 创建默认命令重试策略。 */
    public DefaultCommandRetryPolicy() {
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public @NotNull RetryDecision evaluate(
            @NotNull DeviceDownlink message,
            @NotNull IotFailureCode failure,
            @NotNull Instant now) {
        if (message.attemptCount() >= 5)
            return RetryDecision.stop(IotFailureCode.COMMAND_RETRY_EXHAUSTED);
        return RetryDecision.retryAt(now.plus(delay(message.attemptCount())));
    }

    /** 返回按尝试次数增长且最高五分钟的退避。 */
    private static @NotNull Duration delay(int attemptCount) {
        if (attemptCount <= 0)
            return RETRY_DELAYS[0];
        if (attemptCount <= RETRY_DELAYS.length)
            return RETRY_DELAYS[attemptCount - 1];
        return Duration.ofMinutes(5);
    }
}
