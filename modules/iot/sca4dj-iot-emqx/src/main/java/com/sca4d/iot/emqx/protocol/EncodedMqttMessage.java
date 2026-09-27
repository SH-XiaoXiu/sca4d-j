package com.sca4d.iot.emqx.protocol;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存协议 encoder 生成的最终 MQTT Topic 和 payload。 */
public class EncodedMqttMessage {

    private final @NotNull String topic;

    private final byte @NotNull [] payload;

    /** @param topic 不含通配符的目标 Topic @param payload 厂商 payload */
    public EncodedMqttMessage(@NotNull String topic, byte @NotNull [] payload) {
        this.topic = Objects.requireNonNull(topic, "MQTT Topic 不能为空");
        this.payload = Objects.requireNonNull(payload, "MQTT payload 不能为空").clone();
    }

    /** @return 目标 Topic */
    public @NotNull String topic() {
        return topic;
    }

    /** @return payload 防御性副本 */
    public byte @NotNull [] payload() {
        return payload.clone();
    }
}
