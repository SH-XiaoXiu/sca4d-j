package com.sca4d.app.server.iot;

import com.sca4d.iot.api.query.DeviceRuntimeSnapshot;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存模块运行态快照和具体应用选定的在线判断结果。 */
public final class IotDeviceRuntimeDetails {

    private final @NotNull DeviceRuntimeSnapshot snapshot;

    private final boolean online;

    /** 创建已经按应用在线策略计算的运行态结果。 */
    public IotDeviceRuntimeDetails(@NotNull DeviceRuntimeSnapshot snapshot, boolean online) {
        this.snapshot = Objects.requireNonNull(snapshot, "Device 运行态快照不能为空");
        this.online = online;
    }

    /** @return 模块读取的连接、reported 和 desired 快照 */
    public @NotNull DeviceRuntimeSnapshot snapshot() {
        return snapshot;
    }

    /** @return 当前应用在线策略的判断结果 */
    public boolean online() {
        return online;
    }
}
