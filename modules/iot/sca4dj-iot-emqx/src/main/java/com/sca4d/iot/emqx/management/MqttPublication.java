package com.sca4d.iot.emqx.management;

import org.jetbrains.annotations.NotNull;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 保存一次通过 EMQX Management API 发布的 MQTT Topic、原始 payload、QoS 和 Retain。 */
public final class MqttPublication {

    private static final int MAX_TOPIC_BYTES = 65_535;

    private static final int MAX_PAYLOAD_BYTES = 268_435_455;

    private final @NotNull String topic;

    private final byte @NotNull [] payload;

    private final int qos;

    private final boolean retain;

    /**
     * 创建 MQTT 发布内容。
     *
     * @param topic 不包含通配符且 UTF-8 编码不超过 65535 字节的 MQTT Topic
     * @param payload 需要原样发布的二进制 payload；允许为空数组
     * @param qos MQTT QoS，范围为 0 至 2
     * @param retain 是否保留为 Topic 最新消息
     */
    public MqttPublication(
            @NotNull String topic,
            byte @NotNull [] payload,
            int qos,
            boolean retain) {
        String checkedTopic = Objects.requireNonNull(topic, "MQTT Topic 不能为空");
        if (checkedTopic.isEmpty()
                || checkedTopic.indexOf('\0') >= 0
                || checkedTopic.indexOf('+') >= 0
                || checkedTopic.indexOf('#') >= 0
                || utf8Length(checkedTopic) > MAX_TOPIC_BYTES) {
            throw new IllegalArgumentException("MQTT 发布 Topic 无效");
        }
        byte[] checkedPayload = Objects.requireNonNull(payload, "MQTT payload 不能为空");
        if (checkedPayload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("MQTT payload 超过协议允许长度");
        }
        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException("MQTT QoS 必须在 0 至 2 之间");
        }
        this.topic = checkedTopic;
        this.payload = checkedPayload.clone();
        this.qos = qos;
        this.retain = retain;
    }

    /** @return 不包含通配符的 MQTT Topic */
    public @NotNull String topic() {
        return topic;
    }

    /** @return 需要原样发布的 payload 副本 */
    public byte @NotNull [] payload() {
        return payload.clone();
    }

    /** @return MQTT QoS，范围为 0 至 2 */
    public int qos() {
        return qos;
    }

    /** @return 是否保留为 Topic 最新消息 */
    public boolean retain() {
        return retain;
    }

    /** 返回严格 UTF-8 编码后的字节数，不能编码的代理字符会被拒绝。 */
    private static int utf8Length(String value) {
        try {
            return StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value))
                    .remaining();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("MQTT 发布 Topic 不是有效 UTF-8", exception);
        }
    }
}
