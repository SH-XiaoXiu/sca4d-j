package com.sca4d.iot.api.message;

import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.protocol.DeviceProtocol;
import org.jetbrains.annotations.NotNull;

/** 使用一种具体协议类型编码并发布标准设备下行消息。 */
public interface DeviceTransport<P extends DeviceProtocol> {

    /** @return 当前 Publisher 接受的具体协议 Java 类型 */
    @NotNull
    Class<P> protocolType();

    /**
     * 发布一条已经由 Worker 持有租约的标准消息。
     *
     * @param protocol 目标 Device 绑定的具体协议
     * @param device 目标完整 Device；Publisher 自行选择直接连接或业务路由
     * @param message 待编码并发送的 desired 或 command
     * @throws RuntimeException 编码、发送或传输确认失败时抛出
     */
    void publish(
            @NotNull P protocol,
            @NotNull Device device,
            @NotNull DeviceDownlink message);
}
