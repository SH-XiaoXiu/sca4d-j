package com.sca4d.iot.emqx.protocol;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

/** 把固定前缀只保留在 MQTT Client ID 中，Topic 使用去掉前缀后的设备身份。 */
public class PrefixedMqttTopicIdentityMapper implements MqttTopicIdentityMapper {

    private final @NotNull String clientIdPrefix;

    /** @param clientIdPrefix MQTT Client ID 固定非空前缀 */
    public PrefixedMqttTopicIdentityMapper(@NotNull String clientIdPrefix) {
        this.clientIdPrefix = TextUtils.requireText(clientIdPrefix, "MQTT Client ID 前缀");
    }

    /** 拒绝不属于当前协议前缀的 Client ID。 */
    @Override
    public @NotNull String topicIdentity(@NotNull String clientId) {
        String checked = TextUtils.requireText(clientId, "MQTT Client ID");
        if (!checked.startsWith(clientIdPrefix) || checked.length() == clientIdPrefix.length()) {
            throw new IllegalArgumentException("MQTT Client ID 不属于当前 Topic 身份映射");
        }
        return checked.substring(clientIdPrefix.length());
    }

    /** 给非空 Topic 身份加上当前协议的 Client ID 前缀。 */
    @Override
    public @NotNull String clientId(@NotNull String topicIdentity) {
        return clientIdPrefix + TextUtils.requireText(topicIdentity, "MQTT Topic 设备身份");
    }
}
