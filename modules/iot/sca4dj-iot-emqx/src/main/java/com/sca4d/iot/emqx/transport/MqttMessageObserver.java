package com.sca4d.iot.emqx.transport;

import org.jetbrains.annotations.NotNull;

/** 接收当前应用 MQTT Runtime 实际收到的消息，用于应用记录自身可见的通信事实。 */
@FunctionalInterface
public interface MqttMessageObserver {

    /**
     * 观察一条已经由当前应用 MQTT 订阅收到的消息。
     *
     * <p>实现失败不会阻止消息进入业务路由，也不会改变 MQTT 确认结果。实现不得把该调用理解为
     * Broker 全局消息、客户端发布历史或订阅生命周期通知。
     *
     * @param message 当前应用实际收到的消息
     */
    void messageReceived(@NotNull MqttMessage message);
}
