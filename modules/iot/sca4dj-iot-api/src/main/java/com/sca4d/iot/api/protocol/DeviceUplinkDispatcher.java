package com.sca4d.iot.api.protocol;

import org.jetbrains.annotations.NotNull;

/** 按 Device 当前绑定的协议解码原始消息，并把标准上行结果交给 IoT 接收入口。 */
public interface DeviceUplinkDispatcher {

    /**
     * 处理传输路由已经定位 Device 的一条上行消息。
     *
     * @param message Device 标识、协议内部消息类型、原始载荷和观察时间
     * @throws RuntimeException Device 或协议不存在、协议不支持上行、解码或接收失败时抛出
     */
    void dispatch(@NotNull DeviceProtocolMessage message);
}
