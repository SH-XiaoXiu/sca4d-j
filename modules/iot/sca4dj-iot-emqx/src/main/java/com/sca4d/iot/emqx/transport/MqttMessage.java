package com.sca4d.iot.emqx.transport;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 EMQX 共享订阅收到的一条 MQTT Topic、原始 Payload、发布标志和平台观察时间。 */
@SuppressWarnings("ClassCanBeRecord") // Payload 需要防御性复制，且项目禁止使用 record。
public class MqttMessage {

    private final @NotNull Instant observedAt;

    private final @NotNull String topic;

    private final boolean retained;

    private final byte @NotNull [] payload;

    private final int qos;

    /**
     * 创建一条不可变 MQTT 上行消息，并复制 Payload。
     *
     * @param topic 实际 MQTT Topic
     * @param payload 原始 Payload；构造后修改该数组不会改变消息
     * @param observedAt 当前应用实例收到消息的时间
     * @param qos Broker 交付消息时使用的 QoS 0、1 或 2
     * @param retained PUBLISH 是否设置 Retain 标志
     * @throws IllegalArgumentException topic 为空白或 qos 超出 0 至 2 时抛出
     */
    public MqttMessage(
            @NotNull String topic,
            byte @NotNull [] payload,
            @NotNull Instant observedAt,
            int qos,
            boolean retained) {
        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException("MQTT QoS 必须在 0 至 2 之间");
        }
        this.topic = TextUtils.requireText(topic, "MQTT Topic");
        this.payload = Objects.requireNonNull(payload, "MQTT Payload 不能为空").clone();
        this.observedAt = Objects.requireNonNull(observedAt, "MQTT 消息观察时间不能为空");
        this.qos = qos;
        this.retained = retained;
    }

    /** @return 实际 MQTT Topic */
    @Contract(pure = true)
    public @NotNull String topic() {
        return topic;
    }

    /** @return 原始 MQTT Payload 副本 */
    @Contract(pure = true)
    public byte @NotNull [] payload() {
        return payload.clone();
    }

    /** @return 原始 MQTT Payload 字节数 */
    @Contract(pure = true)
    public int payloadSize() {
        return payload.length;
    }

    /** @return 当前应用实例收到消息的时间 */
    @Contract(pure = true)
    public @NotNull Instant observedAt() {
        return observedAt;
    }

    /** @return Broker 向当前应用交付消息时使用的 MQTT QoS */
    @Contract(pure = true)
    public int qos() {
        return qos;
    }

    /** @return Broker 向当前应用交付的 PUBLISH 是否设置 Retain 标志 */
    @Contract(pure = true)
    public boolean retained() {
        return retained;
    }
}
