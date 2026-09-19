package com.sca4d.iot.message.policy;

import com.sca4d.iot.api.message.AcknowledgementDecision;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlinkMessage;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 只在 reported state 已经与当前 desired sequence 相等时完成状态同步。 */
public final class ReportedEqualsDesiredAcknowledgementPolicy implements AcknowledgementPolicy {

    /** 创建等待 reported 收敛的 ACK 策略。 */
    public ReportedEqualsDesiredAcknowledgementPolicy() {
    }

    @Override
    public @NotNull String code() {
        return "reported-equals-desired";
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
        return "reported-equals-desired".equals(acknowledgement.ackCode())
                ? AcknowledgementDecision.succeed()
                : AcknowledgementDecision.waitForMore();
    }
}
