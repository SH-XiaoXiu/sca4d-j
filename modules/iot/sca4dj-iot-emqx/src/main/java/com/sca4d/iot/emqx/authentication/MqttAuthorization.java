package com.sca4d.iot.emqx.authentication;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 保存当前 Device MQTT ACL 及其稳定指纹。 */
public class MqttAuthorization {

    private final @NotNull List<EmqxAclRule> rules;

    private final @NotNull String fingerprint;

    /** 创建已经完成闭合校验的 MQTT 授权结果。 */
    public MqttAuthorization(@NotNull List<EmqxAclRule> rules, @NotNull String fingerprint) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "MQTT ACL 不能为空"));
        this.fingerprint = Objects.requireNonNull(fingerprint, "MQTT ACL 指纹不能为空");
    }

    /** @return 交给 EMQX HTTP Authenticator 的完整有序 ACL */
    public @NotNull List<EmqxAclRule> rules() {
        return rules;
    }

    /** @return 当前完整 ACL 的 SHA-256 指纹 */
    public @NotNull String fingerprint() {
        return fingerprint;
    }
}
