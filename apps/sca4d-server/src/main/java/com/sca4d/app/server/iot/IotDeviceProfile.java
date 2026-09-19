package com.sca4d.app.server.iot;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 Sca4d Server 为公共 IoT Device 增加的启停状态。 */
public final class IotDeviceProfile {

    private final long deviceId;

    private final @NotNull IotProfileStatus status;

    private final @NotNull Instant createdAt;

    private final @NotNull Instant updatedAt;

    /** 创建数据库返回的 Device App Profile。 */
    public IotDeviceProfile(
            long deviceId,
            @NotNull IotProfileStatus status,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt) {
        this.deviceId = deviceId;
        this.status = Objects.requireNonNull(status, "Device App Profile 状态不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "Device App Profile 创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Device App Profile 修改时间不能为空");
    }

    /** @return 公共 Device 标识 */
    public long deviceId() {
        return deviceId;
    }

    /** @return Sca4d Server 当前启停状态 */
    public @NotNull IotProfileStatus status() {
        return status;
    }

    /** @return App Profile 创建时间 */
    public @NotNull Instant createdAt() {
        return createdAt;
    }

    /** @return App Profile 最近修改时间 */
    public @NotNull Instant updatedAt() {
        return updatedAt;
    }
}
