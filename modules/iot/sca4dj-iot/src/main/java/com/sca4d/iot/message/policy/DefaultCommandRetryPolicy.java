package com.sca4d.iot.message.policy;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryContext;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.api.message.RetryDecision;
import com.sca4d.iot.api.message.RetryPolicy;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

/** 使用预设退避序列并在第五次发送仍失败后停止的默认命令重试策略。 */
public class DefaultCommandRetryPolicy implements RetryPolicy {

    public static final @NotNull TreeCode CODE = TreeCode.of("IOT/DOWNLINK/RETRY/COMMAND/DEFAULT");

    private static final com.sca4d.core.api.retry.RetryPolicy<IotFailureCode> RETRY = com.sca4d.core.api.retry.RetryPolicy
            .always(
                    RetryRule.atMost(
                            5,
                            Backoff.sequence(
                                    Duration.ofSeconds(1),
                                    Duration.ofSeconds(5),
                                    Duration.ofSeconds(30),
                                    Duration.ofMinutes(2),
                                    Duration.ofMinutes(5))));

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
        var decision = RETRY.evaluate(RetryContext.failed(Math.max(1, message.attemptCount()), failure));
        return decision.retryDelay()
                .map(delay -> RetryDecision.retryAt(now.plus(delay)))
                .orElseGet(() -> RetryDecision.stop(IotFailureCode.COMMAND_RETRY_EXHAUSTED));
    }
}
