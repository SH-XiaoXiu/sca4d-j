package com.sca4d.iot.api.message;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 根据标准下行消息、当前状态和统一 ACK 判断业务结果。 */
public interface AcknowledgementPolicy {
    /** @return App 内唯一且跨部署稳定的策略 code */
    @NotNull
    TreeCode code();

    /** @return publish 成功后是否仍需等待设备显式 ACK 或 reported 推导 ACK */
    boolean requiresAcknowledgement();

    /** @return 无副作用策略判断 */
    @CheckReturnValue
    @NotNull
    AcknowledgementDecision evaluate(
            @NotNull DeviceDownlink message,
            @NotNull DeviceAcknowledgement acknowledgement,
            @NotNull Instant now);
}
