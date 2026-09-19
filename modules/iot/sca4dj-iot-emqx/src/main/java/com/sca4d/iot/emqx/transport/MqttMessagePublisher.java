package com.sca4d.iot.emqx.transport;

import com.sca4d.iot.emqx.protocol.EncodedMqttMessage;
import org.jetbrains.annotations.NotNull;

/** 发布协议已经编码完成的 MQTT Topic 和 payload。 */
@FunctionalInterface
public interface MqttMessagePublisher {

    /**
     * 使用 QoS 1、Retain=false 发布消息，并等待 Broker 返回 PUBACK。
     *
     * @param message 不含通配符的目标 Topic 和协议 payload
     */
    void publish(@NotNull EncodedMqttMessage message);
}
