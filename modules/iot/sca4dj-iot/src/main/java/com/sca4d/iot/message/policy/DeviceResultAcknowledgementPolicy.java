package com.sca4d.iot.message.policy;

import com.sca4d.iot.api.message.AcknowledgementDecision;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlinkMessage;
import com.sca4d.iot.api.message.IotFailureCode;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 根据设备显式返回的 `success` 或 `failed` ackCode 判断下行结果。 */
public final class DeviceResultAcknowledgementPolicy implements AcknowledgementPolicy {

    /** 创建解释设备显式结果的 ACK 策略。 */
    public DeviceResultAcknowledgementPolicy() {
    }

    @Override
    public @NotNull String code() {
        return "device-result";
    }

    @Override
    public boolean requiresAcknowledgement() {
        return true;
    }

    @Override
    public @NotNull AcknowledgementDecision evaluate(
            @NotNull DeviceDownlinkMessage message,
            @NotNull DeviceAcknowledgement acknowledgement,
            @NotNull Instant now) {
        if ("success".equalsIgnoreCase(acknowledgement.ackCode())) {
            return AcknowledgementDecision.succeed();
        }
        if ("failed".equalsIgnoreCase(acknowledgement.ackCode())) {
            return AcknowledgementDecision.fail(IotFailureCode.DEVICE_REPORTED_FAILURE);
        }
        return AcknowledgementDecision.waitForMore();
    }
}
