package com.sca4d.iot.emqx.protocol;

import org.jetbrains.annotations.NotNull;

/** 在 MQTT Client ID 与 Topic 中的设备身份之间执行可逆转换。 */
public interface MqttTopicIdentityMapper {

    /** @param clientId Device 直接连接使用的 MQTT Client ID @return Topic 中使用的设备身份 */
    @NotNull
    String topicIdentity(@NotNull String clientId);

    /** @param topicIdentity Topic 中解析出的设备身份 @return Device 直接连接使用的 MQTT Client ID */
    @NotNull
    String clientId(@NotNull String topicIdentity);
}
