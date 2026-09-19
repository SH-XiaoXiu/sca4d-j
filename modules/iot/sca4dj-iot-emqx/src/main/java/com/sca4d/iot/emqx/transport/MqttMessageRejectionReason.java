package com.sca4d.iot.emqx.transport;

/** 区分 MQTT 上行无法通过再次处理恢复的 Topic、身份和 Payload 错误。 */
public enum MqttMessageRejectionReason {
    /** Topic 结构或其中的协议身份不符合当前路由。 */
    TOPIC_INVALID,
    /** Topic 指向的 MQTT Device 身份不存在或不可使用。 */
    IDENTITY_INVALID,
    /** Payload 或协议字段不符合当前消息定义。 */
    MESSAGE_INVALID
}
