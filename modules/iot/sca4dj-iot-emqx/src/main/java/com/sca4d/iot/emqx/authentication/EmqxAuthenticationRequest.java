package com.sca4d.iot.emqx.authentication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存 EMQX HTTP Authenticator 转发的 MQTT CONNECT 和公开客户端证书字段。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class EmqxAuthenticationRequest {

    private final @NotNull String clientId;

    private final @Nullable String username;

    private final @Nullable String password;

    private final @Nullable String certificatePem;

    /** 创建不解释凭据内容的 EMQX 认证请求。 */
    @JsonCreator
    public EmqxAuthenticationRequest(
            @JsonProperty("clientid") @NotNull String clientId,
            @JsonProperty("username") @Nullable String username,
            @JsonProperty("password") @Nullable String password,
            @JsonProperty("cert_pem") @Nullable String certificatePem) {
        this.clientId = Objects.requireNonNull(clientId, "EMQX clientId 不能为空");
        this.username = username;
        this.password = password;
        this.certificatePem = certificatePem;
    }

    /** @return MQTT CONNECT clientId */
    @Contract(pure = true)
    public @NotNull String clientId() {
        return clientId;
    }

    /** @return MQTT CONNECT username；具体应用决定怎样解释该值 */
    @Contract(pure = true)
    public @Nullable String username() {
        return username;
    }

    /** @return 共享 Secret 或 Session MQTT Token */
    @Contract(pure = true)
    public @Nullable String password() {
        return password;
    }

    /** @return EMQX 从已验证 TLS 通道提供的客户端公开证书 PEM */
    @Contract(pure = true)
    public @Nullable String certificatePem() {
        return certificatePem;
    }
}
