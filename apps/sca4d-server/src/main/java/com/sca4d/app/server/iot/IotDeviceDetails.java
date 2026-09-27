package com.sca4d.app.server.iot;

import com.sca4d.iot.api.Device;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 把公共 Device 与 Sca4d Server 启停 Profile 组合成应用查询结果。 */
public class IotDeviceDetails {

    private final @NotNull Device device;

    private final @NotNull IotDeviceProfile profile;

    /** 创建标识一致的 Device 应用查询结果。 */
    public IotDeviceDetails(
            @NotNull Device device,
            @NotNull IotDeviceProfile profile) {
        this.device = Objects.requireNonNull(device, "Device 不能为空");
        this.profile = Objects.requireNonNull(profile, "Device App Profile 不能为空");
        if (device.id() != profile.deviceId()) {
            throw new IllegalArgumentException("Device 与 App Profile 标识不一致");
        }
    }

    /** @return 公共 Device */
    public @NotNull Device device() {
        return device;
    }

    /** @return Sca4d Server Device Profile */
    public @NotNull IotDeviceProfile profile() {
        return profile;
    }
}
