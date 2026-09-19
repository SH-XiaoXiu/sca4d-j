package com.sca4d.iot.message.policy;

import com.sca4d.iot.api.message.DeviceDownlinkMessage;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.api.message.RetryDecision;
import com.sca4d.iot.api.message.RetryPolicy;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 任一传输失败、业务失败或 ACK 超时发生后立即停止下行重试。 */
public final class NoRetryPolicy implements RetryPolicy {

    /** 创建不重试策略。 */
    public NoRetryPolicy() {
    }

    @Override
    public @NotNull String code() {
        return "no-retry";
    }

    @Override
    public @NotNull RetryDecision evaluate(
            @NotNull DeviceDownlinkMessage message,
            @NotNull IotFailureCode failure,
            @NotNull Instant now) {
        return RetryDecision.stop(IotFailureCode.RETRY_DISABLED);
    }
}
