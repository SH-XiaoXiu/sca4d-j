package com.sca4d.app.server.iot;

import com.sca4d.iot.api.change.DeviceChange;
import com.sca4d.iot.api.change.DeviceChangeArea;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 返回需要浏览器重新读取的 Device 数据区域，不携带权威业务状态。 */
@SuppressWarnings("unused")
public class IotDeviceInvalidation {

    private final long deviceId;

    private final @NotNull Set<DeviceChangeArea> areas;

    private final @NotNull Instant occurredAt;

    /** 从模块已提交变化创建允许丢失的 App Realtime payload。 */
    public IotDeviceInvalidation(@NotNull DeviceChange change) {
        DeviceChange checked = Objects.requireNonNull(change, "Device 变化不能为空");
        this.deviceId = checked.deviceId();
        this.areas = Set.copyOf(checked.areas());
        this.occurredAt = checked.occurredAt();
    }

    /** @return 发生变化的 Device 标识 */
    public long getDeviceId() {
        return deviceId;
    }

    /** @return 浏览器需要重新读取的数据区域 */
    public @NotNull @Unmodifiable Set<DeviceChangeArea> getAreas() {
        return areas;
    }

    /** @return 变化提交时间 */
    public @NotNull Instant getOccurredAt() {
        return occurredAt;
    }
}
