package com.sca4d.iot.message.policy;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.api.message.RetryDecision;
import com.sca4d.iot.api.message.RetryPolicy;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 任一传输失败、业务失败或 ACK 超时发生后立即停止下行重试。 */
public class NoRetryPolicy implements RetryPolicy {

    /** 不重试策略的稳定编码，`DevicePolicyCatalog` 用它查找该策略实例。 */
    public static final @NotNull TreeCode CODE = TreeCode.of("IOT/DOWNLINK/RETRY/NONE");

    public NoRetryPolicy() {
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
        return RetryDecision.stop(IotFailureCode.RETRY_DISABLED);
    }
}
