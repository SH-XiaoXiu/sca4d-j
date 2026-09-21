package com.sca4d.core.api.messaging;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 指示 RabbitMQ 对当前 Handler 收到的消息执行确认、再次处理或拒绝。 */
public final class HandlingDecision {

    public enum Status {
        ACKNOWLEDGE,
        RETRY,
        REJECT
    }

    private final @NotNull Status status;

    private final @Nullable String detail;

    private HandlingDecision(@NotNull Status status, @Nullable String detail) {
        this.status = Objects.requireNonNull(status, "消息处理决定不能为空");
        this.detail = detail == null || detail.isBlank() ? null : detail.strip();
    }

    @CheckReturnValue
    public static @NotNull HandlingDecision acknowledge() {
        return new HandlingDecision(Status.ACKNOWLEDGE, null);
    }

    @CheckReturnValue
    public static @NotNull HandlingDecision retry(@NotNull String detail) {
        return new HandlingDecision(Status.RETRY, detail);
    }

    @CheckReturnValue
    public static @NotNull HandlingDecision reject(@NotNull String detail) {
        return new HandlingDecision(Status.REJECT, detail);
    }

    public @NotNull Status status() {
        return status;
    }

    public @Nullable String detail() {
        return detail;
    }
}
