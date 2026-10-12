package com.sca4d.iot.message.policy;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryContext;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.api.message.RetryDecision;
import com.sca4d.iot.api.message.RetryPolicy;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

/** 按预设退避序列持续发送当前 desired sequence，直到新 desired sequence 取代或 reported 收敛。 */
public class DefaultStateSyncRetryPolicy implements RetryPolicy {

    public static final @NotNull TreeCode CODE = TreeCode.of("IOT/DOWNLINK/RETRY/STATE/SYNC/DEFAULT");

    private static final com.sca4d.core.api.retry.RetryPolicy<IotFailureCode> RETRY = com.sca4d.core.api.retry.RetryPolicy
            .always(
                    RetryRule.unlimited(
                            Backoff.sequence(
                                    Duration.ofSeconds(1),
                                    Duration.ofSeconds(5),
                                    Duration.ofSeconds(30),
                                    Duration.ofMinutes(2),
                                    Duration.ofMinutes(5))));

    public DefaultStateSyncRetryPolicy() {
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
        if (message.kind() != DeviceDownlinkKind.DESIRED) {
            return RetryDecision.stop(IotFailureCode.RETRY_POLICY_MISMATCH);
        }
        var decision = RETRY.evaluate(RetryContext.failed(Math.max(1, message.attemptCount()), failure));
        return decision.retryDelay()
                .map(delay -> RetryDecision.retryAt(now.plus(delay)))
                .orElseGet(() -> RetryDecision.stop(IotFailureCode.RETRY_POLICY_MISMATCH));
    }
}
