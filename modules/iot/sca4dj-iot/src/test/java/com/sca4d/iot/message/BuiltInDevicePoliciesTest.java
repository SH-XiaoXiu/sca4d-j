package com.sca4d.iot.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.iot.api.message.AcknowledgementDecision;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkMessage;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.api.message.RetryDecision;
import com.sca4d.iot.api.message.RetryPolicy;
import com.sca4d.iot.message.policy.DefaultCommandRetryPolicy;
import com.sca4d.iot.message.policy.DeviceResultAcknowledgementPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** 验证内置 ACK 与 retry code 对稳定业务输入给出确定状态判断。 */
class BuiltInDevicePoliciesTest {

    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");

    @Test
    @DisplayName("device-result 区分成功失败和继续等待")
    void interpretsOpenDeviceResultAcknowledgements() {
        AcknowledgementPolicy policy = new DeviceResultAcknowledgementPolicy();
        DeviceDownlinkMessage message = command(1);

        assertThat(policy.evaluate(message, deviceAcknowledgement("success"), NOW).outcome())
                .isEqualTo(AcknowledgementDecision.Outcome.SUCCEED);
        assertThat(policy.evaluate(message, deviceAcknowledgement("failed"), NOW).outcome())
                .isEqualTo(AcknowledgementDecision.Outcome.FAIL);
        assertThat(policy.evaluate(message, deviceAcknowledgement("accepted"), NOW).outcome())
                .isEqualTo(AcknowledgementDecision.Outcome.WAIT);
    }

    @Test
    @DisplayName("command-default 使用确定退避并在第五次后停止")
    void retriesCommandsWithBoundedAttempts() {
        RetryPolicy policy = new DefaultCommandRetryPolicy();

        RetryDecision first = policy.evaluate(command(1), IotFailureCode.ACK_TIMEOUT, NOW);
        RetryDecision exhausted = policy.evaluate(command(5), IotFailureCode.ACK_TIMEOUT, NOW);

        assertThat(first.retryAt()).contains(Instant.parse("2026-08-02T10:00:01Z"));
        assertThat(exhausted.retryAt()).isEmpty();
        assertThat(exhausted.stopFailure()).contains(IotFailureCode.COMMAND_RETRY_EXHAUSTED);
    }

    private static DeviceAcknowledgement deviceAcknowledgement(String code) {
        return new DeviceAcknowledgement(1, code, Map.of(), "DEVICE", "ack-1", NOW, NOW);
    }

    private static DeviceDownlinkMessage command(int attemptCount) {
        return new DeviceDownlinkMessage(
                1,
                1,
                1,
                DeviceDownlinkKind.COMMAND,
                "set-mode",
                null,
                null,
                null,
                Map.of("mode", "cool"),
                new DeviceResultAcknowledgementPolicy(),
                Duration.ofSeconds(30),
                new DefaultCommandRetryPolicy(),
                DeviceDownlinkStatus.WAITING_ACK,
                attemptCount,
                null,
                NOW);
    }
}
