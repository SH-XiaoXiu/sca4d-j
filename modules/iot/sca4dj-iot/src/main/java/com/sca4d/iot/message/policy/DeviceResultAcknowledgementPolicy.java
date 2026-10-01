package com.sca4d.iot.message.policy;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.iot.api.message.AcknowledgementDecision;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.IotFailureCode;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 根据设备显式返回的 `success` 或 `failed` ackCode 判断下行结果。 */
public class DeviceResultAcknowledgementPolicy implements AcknowledgementPolicy {

    public static final @NotNull TreeCode CODE = TreeCode.of("IOT/DOWNLINK/ACK/DEVICE/RESULT");

    public DeviceResultAcknowledgementPolicy() {
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public boolean requiresAcknowledgement() {
        return true;
    }

    @Override
    public @NotNull AcknowledgementDecision evaluate(
            @NotNull DeviceDownlink message,
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
