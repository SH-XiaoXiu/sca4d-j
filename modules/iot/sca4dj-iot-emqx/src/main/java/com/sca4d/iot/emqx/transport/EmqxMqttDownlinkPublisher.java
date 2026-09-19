package com.sca4d.iot.emqx.transport;

import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.message.DeviceDownlinkMessage;
import com.sca4d.iot.api.message.DeviceDownlinkPublisher;
import com.sca4d.iot.emqx.connection.MqttDeviceConnection;
import com.sca4d.iot.emqx.protocol.MqttDeviceProtocol;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 使用目标 Device 的 MQTT 协议和已登记 Client ID 编码下行，并等待 EMQX PUBACK。 */
public final class EmqxMqttDownlinkPublisher implements DeviceDownlinkPublisher<MqttDeviceProtocol> {

    private final @NotNull MqttMessagePublisher publisher;

    /** @param publisher 已编码 MQTT 消息的发布入口 */
    public EmqxMqttDownlinkPublisher(@NotNull MqttMessagePublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "MQTT 消息发布器不能为空");
    }

    /** @return MQTT 协议公开类型 */
    @Override
    public @NotNull Class<MqttDeviceProtocol> protocolType() {
        return MqttDeviceProtocol.class;
    }

    /** publish 成功只表示 EMQX 已返回 PUBACK，不表示业务 ACK 成功。 */
    @Override
    public void publish(
            @NotNull MqttDeviceProtocol protocol,
            @NotNull Device device,
            @NotNull DeviceDownlinkMessage message) {
        String clientId = MqttDeviceConnection.clientId(
                Objects.requireNonNull(device, "Device 不能为空"));
        publisher.publish(
                Objects.requireNonNull(protocol, "MQTT DeviceProtocol 不能为空")
                        .encode(
                                Objects.requireNonNull(message, "Device 下行消息不能为空"),
                                clientId));
    }
}
