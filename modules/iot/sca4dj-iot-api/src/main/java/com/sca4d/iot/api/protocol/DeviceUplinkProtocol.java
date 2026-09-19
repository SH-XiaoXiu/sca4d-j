package com.sca4d.iot.api.protocol;

import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.message.DeviceUplink;
import org.jetbrains.annotations.NotNull;

/** 声明能够把传输无关原始消息解码为标准 IoT 上行结果的 DeviceProtocol。 */
public interface DeviceUplinkProtocol extends DeviceProtocol {

    /**
     * 解码已经由传输路由定位到当前 Device 的原始消息。
     *
     * @param device 当前绑定该协议的 Device
     * @param message 协议消息类型、原始载荷和平台观察时间
     * @return 一次标准 reported 或 acknowledgement 结果
     */
    @NotNull
    DeviceUplink decode(@NotNull Device device, @NotNull DeviceProtocolMessage message);
}
