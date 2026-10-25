package com.sca4d.iot.protocol;

import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.message.DeviceUplink;
import com.sca4d.iot.api.message.DeviceUplinkReceiver;
import com.sca4d.iot.api.protocol.DeviceProtocol;
import com.sca4d.iot.api.protocol.DeviceProtocolMessage;
import com.sca4d.iot.api.protocol.DeviceUplinkDispatcher;
import com.sca4d.iot.api.protocol.DeviceUplinkProtocol;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** 使用 Device 台账选择实际协议，并分发协议解码后的标准上行结果。 */
public class StandardDeviceUplinkDispatcher implements DeviceUplinkDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(StandardDeviceUplinkDispatcher.class);

    private final @NotNull DeviceProtocolCatalog protocols;

    private final @NotNull DeviceUplinkReceiver receiver;

    /**
     * 创建上行分发器。
     *
     * @param protocols 按 code 查找已安装协议的目录
     * @param receiver 接收解码后状态和 ACK 的 Device 上行处理入口
     */
    public StandardDeviceUplinkDispatcher(
            @NotNull DeviceProtocolCatalog protocols,
            @NotNull DeviceUplinkReceiver receiver) {
        this.protocols = Objects.requireNonNull(protocols, "DeviceProtocolCatalog 不能为空");
        this.receiver = Objects.requireNonNull(receiver, "DeviceUplinkReceiver 不能为空");
    }

    /** Device 当前绑定的协议是唯一解码依据，传输路由不指定协议 code。 */
    @Override
    public void dispatch(@NotNull DeviceProtocolMessage message) {
        DeviceProtocolMessage input = Objects.requireNonNull(message, "Device 协议消息不能为空");
        Device device = input.device();
        DeviceProtocol protocol = protocols.loadByCode(device.protocolCode());
        if (!(protocol instanceof DeviceUplinkProtocol uplinkProtocol)) {
            throw new IllegalArgumentException("DeviceProtocol 不支持上行消息: " + protocol.code());
        }
        DeviceUplink uplink = uplinkProtocol.decode(device, input);
        if (uplink.reportedState().isPresent()) {
            receiver.reported(
                    device,
                    uplink.reportedState().orElseThrow(),
                    uplink.events());
        }
        if (uplink.acknowledgement().isPresent()) {
            receiver.acknowledged(device, uplink.acknowledgement().orElseThrow());
        }
        LOG.trace(
                "Device 上行消息已分发 | deviceId={} protocol={} messageCode={} kind={}",
                device.id(),
                protocol.code(),
                input.code(),
                uplink.kind());
    }
}
