package com.sca4d.openapi.client;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 Client 创建或轮换时只允许返回一次的明文凭据。 */
public final class OpenApiClientCredentials {

    private final @NotNull OpenApiClient client;

    private final @NotNull String method;

    private final @NotNull String secret;

    /** 创建一次性 Client 凭据结果。 */
    public OpenApiClientCredentials(
            @NotNull OpenApiClient client,
            @NotNull String method,
            @NotNull String secret) {
        this.client = Objects.requireNonNull(client, "OpenAPI Client 不能为空");
        this.method = TextUtils.requireText(method, "认证方法");
        this.secret = TextUtils.requireText(secret, "Client 凭据");
    }

    /** @return Client 元数据 */
    public @NotNull OpenApiClient client() {
        return client;
    }

    /** @return shared-secret 或 hmac */
    public @NotNull String method() {
        return method;
    }

    /** @return 仅当前响应可见的明文 Secret 或 MAC Key */
    public @NotNull String secret() {
        return secret;
    }

}
