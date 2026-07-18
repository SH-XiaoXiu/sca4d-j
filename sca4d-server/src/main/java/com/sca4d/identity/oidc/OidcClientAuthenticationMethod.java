package com.sca4d.identity.oidc;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** OIDC Token Endpoint 接受的 Client Secret 发送方式。 */
public enum OidcClientAuthenticationMethod implements BaseEnum {
    /** 使用 HTTP Basic 发送经过表单编码的 Client ID 与 Client Secret。 */
    CLIENT_SECRET_BASIC("client_secret_basic", "Client Secret Basic"),
    /** 在 Token Request 表单中发送 Client ID 与 Client Secret。 */
    CLIENT_SECRET_POST("client_secret_post", "Client Secret Post");

    private final String value;
    private final String description;

    OidcClientAuthenticationMethod(@NotNull String value, @NotNull String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
