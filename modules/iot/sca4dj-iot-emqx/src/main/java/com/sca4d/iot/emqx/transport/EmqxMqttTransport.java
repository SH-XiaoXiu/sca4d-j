package com.sca4d.iot.emqx.transport;

import com.sca4d.iot.emqx.protocol.EncodedMqttMessage;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Objects;

/** 延迟取得 `EmqxMqttRuntime`，使 MQTT 消息路由能够返回协议响应。 */
public class EmqxMqttTransport implements MqttTransport {

    private final @NotNull ObjectProvider<EmqxMqttRuntime> runtimes;

    /**
     * 创建不会在路由 Bean 装配期间提前取得 MQTT 运行时的发布器。
     *
     * @param runtimes 当前应用的 MQTT 运行时提供器
     */
    public EmqxMqttTransport(@NotNull ObjectProvider<EmqxMqttRuntime> runtimes) {
        this.runtimes = Objects.requireNonNull(runtimes, "EMQX MQTT 运行时提供器不能为空");
    }

    /** 发布时才取得已经完成 Bean 装配的 MQTT 运行时。 */
    @Override
    public void publish(@NotNull EncodedMqttMessage message) {
        runtimes.getObject().publish(Objects.requireNonNull(message, "MQTT 下行消息不能为空"));
    }
}
