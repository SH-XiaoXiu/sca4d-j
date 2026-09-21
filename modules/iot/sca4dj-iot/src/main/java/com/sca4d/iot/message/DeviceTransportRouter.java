package com.sca4d.iot.message;

import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceTransport;
import com.sca4d.iot.api.protocol.DeviceProtocol;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.protocol.DeviceProtocolCatalog;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 按 Device 当前绑定的具体协议类型选择唯一的下行 Publisher。 */
public final class DeviceTransportRouter {

    private final @NotNull DeviceRepository devices;

    private final @NotNull DeviceProtocolCatalog protocols;

    private final @NotNull Map<Class<? extends DeviceProtocol>, DeviceTransport<?>> publishers;

    /**
     * @param devices Device 及其连接身份持久化入口
     * @param protocols 具体应用登记的协议目录
     * @param publishers 具体设备接入实现提供的下行 Publisher
     */
    public DeviceTransportRouter(
            @NotNull DeviceRepository devices,
            @NotNull DeviceProtocolCatalog protocols,
            @NotNull Collection<DeviceTransport<?>> publishers) {
        this.devices = Objects.requireNonNull(devices, "Device Repository 不能为空");
        this.protocols = Objects.requireNonNull(protocols, "DeviceProtocolCatalog 不能为空");
        LinkedHashMap<Class<? extends DeviceProtocol>, DeviceTransport<?>> indexed = new LinkedHashMap<>();
        for (DeviceTransport<?> publisher : Objects.requireNonNull(
                publishers,
                "DeviceTransport 集合不能为空")) {
            DeviceTransport<?> checked = Objects.requireNonNull(
                    publisher,
                    "DeviceTransport 不能为空");
            if (indexed.putIfAbsent(checked.protocolType(), checked) != null) {
                throw new IllegalArgumentException(
                        "同一 DeviceProtocol 类型登记了多个下行 Publisher: "
                                + checked.protocolType().getName());
            }
        }
        this.publishers = Map.copyOf(indexed);
    }

    /**
     * 校验消息绑定版本，并把完整 Device 交给匹配 Publisher。
     *
     * @param message 已由 Worker 持有数据库租约的标准下行消息
     */
    public void publish(@NotNull DeviceDownlink message) {
        DeviceDownlink checked = Objects.requireNonNull(message, "Device 下行消息不能为空");
        Device device = devices.find(checked.deviceId())
                .orElseThrow(() -> new IllegalStateException("下行消息引用的 Device 不存在"));
        if (device.bindingGeneration() != checked.bindingGeneration()) {
            throw new IllegalStateException("Device 物模型绑定代数 已变化");
        }
        DeviceProtocol protocol = protocols.loadByCode(device.protocolCode());
        DeviceTransport<?> publisher = publishers.get(protocol.getClass());
        if (publisher == null) {
            throw new IllegalStateException("DeviceProtocol 没有匹配的下行 Publisher: " + protocol.code());
        }
        publish(publisher, protocol, device, checked);
    }

    /** 在 Publisher 声明的 Java 类型边界执行受检转换。 */
    private static <P extends DeviceProtocol> void publish(
            DeviceTransport<P> publisher,
            DeviceProtocol protocol,
            Device device,
            DeviceDownlink message) {
        publisher.publish(publisher.protocolType().cast(protocol), device, message);
    }
}
