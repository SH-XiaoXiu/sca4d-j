package com.sca4d.iot.persistence;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存批量查询返回的 Device 标识和接入方式编码。 */
public final class DeviceAccessMethodState {

    private final long deviceId;

    private final @NotNull String methodCode;

    /** @param deviceId Device 标识 @param methodCode Device 使用的接入方式编码 */
    public DeviceAccessMethodState(long deviceId, @NotNull String methodCode) {
        this.deviceId = deviceId;
        this.methodCode = Objects.requireNonNull(methodCode, "Device 接入方式编码不能为空");
    }

    /** @return Device 标识 */
    public long deviceId() {
        return deviceId;
    }

    /** @return Device 使用的接入方式编码 */
    public @NotNull String methodCode() {
        return methodCode;
    }
}
