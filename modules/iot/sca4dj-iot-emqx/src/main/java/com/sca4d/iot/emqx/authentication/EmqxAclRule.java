package com.sca4d.iot.emqx.authentication;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** 表示 EMQX `5.8` HTTP Authenticator 响应中的一条新格式 ACL 规则。 */
public class EmqxAclRule {

    private final @Nullable Boolean retain;

    private final @NotNull List<Integer> qos;

    private final @NotNull String action;

    private final @NotNull String permission;

    private final @NotNull String topic;

    /** 创建一条明确允许或拒绝 Topic 操作的 ACL 规则。 */
    public EmqxAclRule(
            @NotNull String permission,
            @NotNull String action,
            @NotNull String topic,
            @NotNull List<Integer> qos,
            @Nullable Boolean retain) {
        this.permission = TextUtils.requireText(permission, "EMQX ACL permission");
        this.action = TextUtils.requireText(action, "EMQX ACL action");
        this.topic = TextUtils.requireText(topic, "EMQX ACL topic");
        this.qos = List.copyOf(Objects.requireNonNull(qos, "EMQX ACL QoS 不能为空"));
        this.retain = retain;
    }

    /** @return `allow` 或 `deny` */
    @JsonProperty("permission")
    public @NotNull String permission() {
        return permission;
    }

    /** @return `publish`、`subscribe` 或 `all` */
    @JsonProperty("action")
    public @NotNull String action() {
        return action;
    }

    /** @return MQTT Topic Filter */
    @JsonProperty("topic")
    public @NotNull String topic() {
        return topic;
    }

    /** @return 规则允许匹配的 MQTT QoS */
    @JsonProperty("qos")
    public @NotNull List<Integer> qos() {
        return qos;
    }

    /** @return publish 规则是否匹配 Retained Message */
    @JsonProperty("retain")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public @Nullable Boolean retain() {
        return retain;
    }
}
