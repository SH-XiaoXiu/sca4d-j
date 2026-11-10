package com.sca4d.iot.emqx.transport;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存应用实例从 EMQX `$SYS` 共享订阅实际收到的 Client 连接或断开事件，不代表当前连接投影。 */
public final class MqttConnectionEvent {

    private final @NotNull Instant observedAt;

    private final @NotNull String clientId;

    private final @NotNull String topic;

    private final boolean connected;

    private final byte @NotNull [] payload;

    /**
     * 创建不可变事件，复制原始 Payload；不校验 Topic 与连接标志是否一致。
     *
     * @param topic 实际收到的 `$SYS/brokers/+/clients/+/connected` 或 `disconnected` Topic
     * @param clientId Payload 中的 `clientid`，不表示该 Client 已登记为 Device
     * @param connected 连接事件为 true，断开事件为 false
     * @param payload EMQX 原始 JSON 字节，包含的字段取决于 Broker 版本
     * @param observedAt 应用实例观察到事件的时间，不是 Broker 连接发生时间
     * @throws IllegalArgumentException Topic 或 Client ID 为空白
     * @throws NullPointerException 任一引用参数为 null
     */
    public MqttConnectionEvent(
            @NotNull String topic,
            @NotNull String clientId,
            boolean connected,
            byte @NotNull [] payload,
            @NotNull Instant observedAt) {
        this.topic = TextUtils.requireText(topic, "MQTT 连接事件 Topic");
        this.clientId = TextUtils.requireText(clientId, "MQTT 连接事件 Client ID");
        this.connected = connected;
        this.payload = Objects.requireNonNull(payload, "MQTT 连接事件 Payload 不能为空").clone();
        this.observedAt = Objects.requireNonNull(observedAt, "MQTT 连接事件观察时间不能为空");
    }

    /** @return 实际收到的系统 Topic */
    @Contract(pure = true)
    public @NotNull String topic() {
        return topic;
    }

    /** @return 原始事件中的 MQTT Client ID */
    @Contract(pure = true)
    public @NotNull String clientId() {
        return clientId;
    }

    /** @return 是否为连接事件；断开事件返回 false */
    @Contract(pure = true)
    public boolean connected() {
        return connected;
    }

    /** @return 原始 Payload 的独立副本，修改副本不影响事件及其他观察器 */
    @Contract(value = "-> new", pure = true)
    public byte @NotNull [] payload() {
        return payload.clone();
    }

    /** @return 应用实例观察到事件的时间 */
    @Contract(pure = true)
    public @NotNull Instant observedAt() {
        return observedAt;
    }
}
