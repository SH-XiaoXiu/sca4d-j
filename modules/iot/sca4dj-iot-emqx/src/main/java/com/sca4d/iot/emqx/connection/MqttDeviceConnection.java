package com.sca4d.iot.emqx.connection;

import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.connection.DeviceConnectionBinding;
import com.sca4d.iot.api.connection.DeviceConnectionDefinition;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 创建并读取 MQTT Client ID 形式的 Device 直接连接绑定。 */
public class MqttDeviceConnection {

    /** MQTT 直接连接使用的稳定身份类型。 */
    public static final @NotNull String IDENTIFIER_TYPE = "mqtt-client-id";

    /** MQTT Client ID 连接定义。 */
    public static final @NotNull DeviceConnectionDefinition DEFINITION = new DeviceConnectionDefinition(
            DeviceConnectionProtocol.MQTT,
            IDENTIFIER_TYPE);

    private MqttDeviceConnection() {
    }

    /** @param clientId Device 在 EMQX 中使用的唯一 Client ID @return MQTT 直接连接绑定 */
    @CheckReturnValue
    public static @NotNull DeviceConnectionBinding clientId(@NotNull String clientId) {
        return new DeviceConnectionBinding(DEFINITION, TextUtils.requireText(clientId, "MQTT Client ID"));
    }

    /**
     * @param device 必须直接使用 MQTT Client ID 连接的 Device
     * @return 已登记 MQTT Client ID
     * @throws IllegalArgumentException Device 没有 MQTT Client ID 连接绑定时抛出
     */
    @CheckReturnValue
    public static @NotNull String clientId(@NotNull Device device) {
        DeviceConnectionBinding connection = Objects.requireNonNull(device, "Device 不能为空")
                .connection()
                .filter(binding -> DEFINITION.equals(binding.definition()))
                .orElseThrow(() -> new IllegalArgumentException("Device 没有 MQTT Client ID 连接绑定"));
        return connection.identifierValue();
    }
}
