package com.sca4d.iot.api.message;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 以互斥的允许、延后或拒绝结果控制发送，不把资源排队当作设备失败。 */
public final class DeviceDispatchDecision {
    private final @Nullable Instant deferredUntil;

    private final @Nullable IotFailureCode failure;

    private DeviceDispatchDecision(@Nullable Instant deferredUntil, @Nullable IotFailureCode failure) {
        this.deferredUntil = deferredUntil;
        this.failure = failure;
    }

    /** @return 允许开始或恢复当前发送尝试 */
    public static @NotNull DeviceDispatchDecision allow() {
        return new DeviceDispatchDecision(null, null);
    }

    /** @return 在指定时间重新检查准入的决定 */
    public static @NotNull DeviceDispatchDecision deferUntil(@NotNull Instant at) {
        return new DeviceDispatchDecision(Objects.requireNonNull(at), null);
    }

    /** @return 不再发送并保存明确失败原因的决定 */
    public static @NotNull DeviceDispatchDecision reject(@NotNull IotFailureCode failure) {
        return new DeviceDispatchDecision(null, Objects.requireNonNull(failure));
    }

    /** @return 延后时间；允许或拒绝时为空 */
    public @NotNull Optional<Instant> deferredUntil() {
        return Optional.ofNullable(deferredUntil);
    }

    /** @return 拒绝原因；允许或延后时为空 */
    public @NotNull Optional<IotFailureCode> failure() {
        return Optional.ofNullable(failure);
    }
}
