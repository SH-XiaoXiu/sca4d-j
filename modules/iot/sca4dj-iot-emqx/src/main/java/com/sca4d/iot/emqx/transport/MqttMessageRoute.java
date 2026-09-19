package com.sca4d.iot.emqx.transport;

import org.jetbrains.annotations.NotNull;

/** 声明一条 MQTT Topic Filter 到目标消息流的路由。 */
public interface MqttMessageRoute {

    /** @return 当前路由需要由共享 MQTT Client 订阅的单个 Topic Filter */
    @NotNull
    String topicFilter();

    /**
     * 路由一条已经匹配当前 Topic Filter 的 QoS 1 消息；正常返回后运行时才确认消息。
     *
     * @param message Topic、原始 Payload 和平台观察时间
     * @throws MqttMessageRejectedException Topic 身份、Payload 或协议字段明确无效时抛出
     */
    void route(@NotNull MqttMessage message);
}
