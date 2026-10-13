package com.sca4d.iot.api.message;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 在发送事务中检查共享传输资源是否允许本次尝试；延后不会消耗发送次数或 ACK 等待时间。 */
@FunctionalInterface
public interface DeviceDispatchAdmission {
    /** @return 本次消息的发送准入结果；实现方可在当前事务内持久化资源占用 */
    @NotNull
    DeviceDispatchDecision admit(@NotNull DeviceDownlink message, int attempt, @NotNull Instant now);
}
