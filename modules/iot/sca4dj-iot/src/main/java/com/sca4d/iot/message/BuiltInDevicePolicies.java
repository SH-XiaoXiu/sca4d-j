package com.sca4d.iot.message;

import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.RetryPolicy;
import com.sca4d.iot.message.policy.DefaultCommandRetryPolicy;
import com.sca4d.iot.message.policy.DefaultStateSyncRetryPolicy;
import com.sca4d.iot.message.policy.DeviceResultAcknowledgementPolicy;
import com.sca4d.iot.message.policy.NoAcknowledgementPolicy;
import com.sca4d.iot.message.policy.NoRetryPolicy;
import com.sca4d.iot.message.policy.ReportedEqualsDesiredAcknowledgementPolicy;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 创建首批演示使用且可以由静态 ThingModel 直接引用的具名策略对象。 */
public class BuiltInDevicePolicies {

    private BuiltInDevicePolicies() {
    }

    /** @return 每个内置 ACK 策略类的新实例 */
    public static @NotNull List<AcknowledgementPolicy> acknowledgements() {
        return List.of(
                new NoAcknowledgementPolicy(),
                new DeviceResultAcknowledgementPolicy(),
                new ReportedEqualsDesiredAcknowledgementPolicy());
    }

    /** @return 每个内置 retry 策略类的新实例 */
    public static @NotNull List<RetryPolicy> retries() {
        return List.of(
                new NoRetryPolicy(),
                new DefaultCommandRetryPolicy(),
                new DefaultStateSyncRetryPolicy());
    }
}
