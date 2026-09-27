package com.sca4d.iot.api.change;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/** 保存一次已经提交的 Device 数据变化及其发生时间。 */
public class DeviceChange {

    private final long deviceId;

    private final @NotNull Set<DeviceChangeArea> areas;

    private final @NotNull Instant occurredAt;

    /** 创建至少包含一个变化区域的 Device 变化通知。 */
    public DeviceChange(
            long deviceId,
            @NotNull Collection<DeviceChangeArea> areas,
            @NotNull Instant occurredAt) {
        if (deviceId <= 0) {
            throw new IllegalArgumentException("Device 标识必须为正数");
        }
        this.deviceId = deviceId;
        this.areas = Set.copyOf(Objects.requireNonNull(areas, "Device 变化区域不能为空"));
        this.occurredAt = Objects.requireNonNull(occurredAt, "Device 变化时间不能为空");
        if (this.areas.isEmpty()) {
            throw new IllegalArgumentException("Device 变化区域不能为空集合");
        }
    }

    /** @return 发生变化的 Device 标识 */
    public long deviceId() {
        return deviceId;
    }

    /** @return 需要调用方重新读取的不可修改区域集合 */
    public @NotNull @Unmodifiable Set<DeviceChangeArea> areas() {
        return areas;
    }

    /** @return 业务状态提交时间 */
    public @NotNull Instant occurredAt() {
        return occurredAt;
    }
}
