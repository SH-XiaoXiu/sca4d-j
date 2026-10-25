package com.sca4d.iot.emqx.authentication;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 表示 EMQX `5.8.9` HTTP Authenticator 必须以 HTTP 200 返回的认证结果。 */
public class EmqxAuthenticationResponse {

    private final @NotNull Result result;

    private final boolean superuser;

    private final @NotNull List<EmqxAclRule> acl;

    private final @Nullable Long expireAt;

    private final @NotNull Map<String, String> clientAttributes;

    private EmqxAuthenticationResponse(
            @NotNull Result result,
            @NotNull Map<String, String> clientAttributes,
            @NotNull List<EmqxAclRule> acl,
            @Nullable Long expireAt) {
        this.result = result;
        this.superuser = false;
        this.clientAttributes = Map.copyOf(clientAttributes);
        this.acl = List.copyOf(acl);
        this.expireAt = expireAt;
    }

    /** @return 明确拒绝且不能由之后的 Authenticator 放宽的响应 */
    @CheckReturnValue
    public static @NotNull EmqxAuthenticationResponse deny() {
        return new EmqxAuthenticationResponse(Result.DENY, Map.of(), List.of(), null);
    }

    /** @return 包含连接属性、ACL 和可选认证失效时间的允许响应 */
    @CheckReturnValue
    public static @NotNull EmqxAuthenticationResponse allow(
            @NotNull Map<String, String> clientAttributes,
            @NotNull List<EmqxAclRule> acl,
            @Nullable Instant expiresAt) {
        return new EmqxAuthenticationResponse(
                Result.ALLOW,
                Objects.requireNonNull(clientAttributes, "EMQX client attrs 不能为空"),
                Objects.requireNonNull(acl, "EMQX ACL 不能为空"),
                expiresAt == null ? null : expiresAt.getEpochSecond());
    }

    /** @return `ALLOW` 或 `DENY`，序列化为 EMQX 要求的 `allow` 或 `deny` */
    @JsonProperty("result")
    public @NotNull Result result() {
        return result;
    }

    /** @return 始终为 false，设备认证不产生 EMQX superuser */
    @JsonProperty("is_superuser")
    public boolean superuser() {
        return superuser;
    }

    /** @return 供连接事件回传的稳定非敏感属性 */
    @JsonProperty("client_attrs")
    public @NotNull Map<String, String> clientAttributes() {
        return clientAttributes;
    }

    /** @return 拒绝优先且结尾拒绝全部的 MQTT ACL */
    @JsonProperty("acl")
    public @NotNull List<EmqxAclRule> acl() {
        return acl;
    }

    /** @return 可选 Unix 秒认证失效时间 */
    @JsonProperty("expire_at")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public @Nullable Long expireAt() {
        return expireAt;
    }

    /** EMQX HTTP Authenticator 的认证结论，只有 `allow` 和 `deny` 两个协议值。 */
    public enum Result {

        /** 允许连接，并使用响应中的 client_attrs、ACL 和 expire_at。 */
        ALLOW("allow"),

        /** 明确拒绝连接，之后的 Authenticator 不能放宽该结果。 */
        DENY("deny");

        private final @NotNull String protocolValue;

        Result(@NotNull String protocolValue) {
            this.protocolValue = protocolValue;
        }

        /** @return 写入 EMQX HTTP Authenticator 响应的小写协议值 */
        @JsonValue
        public @NotNull String protocolValue() {
            return protocolValue;
        }
    }
}
