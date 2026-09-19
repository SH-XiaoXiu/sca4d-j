package com.sca4d.iot.message.policy;

import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkMessage;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.api.message.RetryDecision;
import com.sca4d.iot.api.message.RetryPolicy;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

/** 按固定退避持续发送当前 desired sequence，直到新 desired sequence 取代或 reported 收敛。 */
public final class DefaultStateSyncRetryPolicy implements RetryPolicy {

    private static final Duration[] RETRY_DELAYS = {
            Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2)
    };

    /** 创建默认 desired 状态同步重试策略。 */
    public DefaultStateSyncRetryPolicy() {
    }

    @Override
    public @NotNull String code() {
        return "state-sync-default";
    }

    @Override
    public @NotNull RetryDecision evaluate(
            @NotNull DeviceDownlinkMessage message,
            @NotNull IotFailureCode failure,
            @NotNull Instant now) {
        if (message.kind() != DeviceDownlinkKind.DESIRED) {
            return RetryDecision.stop(IotFailureCode.RETRY_POLICY_MISMATCH);
        }
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
