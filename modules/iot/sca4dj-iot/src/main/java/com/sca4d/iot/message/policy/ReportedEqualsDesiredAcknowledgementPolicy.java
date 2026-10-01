package com.sca4d.iot.message.policy;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.iot.api.message.AcknowledgementDecision;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlink;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 只在 reported state 已经与当前 desired sequence 相等时完成状态同步。 */
public class ReportedEqualsDesiredAcknowledgementPolicy implements AcknowledgementPolicy {

    public static final @NotNull TreeCode CODE = TreeCode.of("IOT/DOWNLINK/ACK/REPORTED/EQUALS/DESIRED");

    public ReportedEqualsDesiredAcknowledgementPolicy() {
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
        return CODE.equals(TreeCode.of(acknowledgement.ackCode()))
                ? AcknowledgementDecision.succeed()
                : AcknowledgementDecision.waitForMore();
    }
}
