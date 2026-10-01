package com.sca4d.core.api.messaging;

import static com.sca4d.core.api.messaging.HandlingDecision.Status.ACKNOWLEDGE;
import static com.sca4d.core.api.messaging.HandlingDecision.Status.REJECT;
import static com.sca4d.core.api.messaging.HandlingDecision.Status.RETRY;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 指示 RabbitMQ 对当前 Handler 收到的消息执行确认、再次处理或拒绝。 */
public class HandlingDecision {

    public enum Status {
        ACKNOWLEDGE,
        RETRY,
        REJECT
    }

    private final @NotNull Status status;

    private final @Nullable String detail;

    /** 创建消息处理决定。 */
    private HandlingDecision(@NotNull Status status, @Nullable String detail) {
        this.status = Objects.requireNonNull(status, "消息处理决定不能为空");
        this.detail = detail == null || detail.isBlank() ? null : detail.strip();
    }

    /** @return 确认当前消息并结束处理 */
    @CheckReturnValue
    public static @NotNull HandlingDecision acknowledge() {
        return new HandlingDecision(ACKNOWLEDGE, null);
    }

    /** @param detail 再次处理原因 @return 要求基础设施再次处理当前消息 */
    @CheckReturnValue
    public static @NotNull HandlingDecision retry(@NotNull String detail) {
        return new HandlingDecision(RETRY, detail);
    }

    /** @param detail 拒绝原因 @return 拒绝当前消息 */
    @CheckReturnValue
    public static @NotNull HandlingDecision reject(@NotNull String detail) {
        return new HandlingDecision(REJECT, detail);
    }

    /** @return 处理决定状态 */
    public @NotNull Status status() {
        return status;
    }

    /** @return 面向日志和重试记录的附加说明 */
    public @Nullable String detail() {
        return detail;
    }
}
