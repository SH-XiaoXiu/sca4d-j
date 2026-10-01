package com.sca4d.iot.message.policy;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.iot.api.message.AcknowledgementDecision;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlink;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 在 MQTT publish 成功后直接完成下行消息，不等待设备业务回执。 */
public class NoAcknowledgementPolicy implements AcknowledgementPolicy {

    public static final @NotNull TreeCode CODE = TreeCode.of("IOT/DOWNLINK/ACK/NONE");

    public NoAcknowledgementPolicy() {
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public boolean requiresAcknowledgement() {
        return false;
    }

    @Override
    public @NotNull AcknowledgementDecision evaluate(
            @NotNull DeviceDownlink message,
            @NotNull DeviceAcknowledgement acknowledgement,
            @NotNull Instant now) {
        return AcknowledgementDecision.succeed();
    }
}
