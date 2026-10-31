package com.sca4d.open.client;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 Client 创建或轮换时只允许返回一次的明文凭据。 */
public class OpenClientCredentials {

    private final @NotNull OpenClient client;

    private final @NotNull String method;

    private final @NotNull String secret;

    /** 创建一次性 Client 凭据结果。 */
    public OpenClientCredentials(
            @NotNull OpenClient client,
            @NotNull String method,
            @NotNull String secret) {
        this.client = Objects.requireNonNull(client, "Open Client 不能为空");
        this.method = TextUtils.requireText(method, "认证方法");
        this.secret = TextUtils.requireText(secret, "Client 凭据");
    }

    /** @return Client 元数据 */
    public @NotNull OpenClient client() {
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
