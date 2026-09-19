package com.sca4d.app.server.openapi.iot;

import com.sca4d.app.server.iot.IotProfileStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表示开放 API 中 IoT Device 是否允许参与运行。 */
@Schema(name = "IotDeviceStatus", description = "Device 当前是否允许认证、连接和交换消息。")
public enum OpenApiIotDeviceStatus {
    /** Device 允许认证、连接和交换消息。 */
    ENABLED,

    /** Device 只允许管理，不允许认证、连接和交换消息。 */
    DISABLED;

    /**
     * 把应用内部启停状态转换为开放 API 枚举。
     *
     * @param status 应用内部 Device 状态
     * @return 对外稳定的 Device 状态
     */
    @CheckReturnValue
    public static @NotNull OpenApiIotDeviceStatus from(@NotNull IotProfileStatus status) {
        return valueOf(Objects.requireNonNull(status, "Device 状态不能为空").name());
    }
}
