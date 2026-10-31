package com.sca4d.openapi.web;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** 按 Open Client 的固定字段顺序生成 HMAC 规范请求。 */
public class OpenApiHmacCanonicalRequest {

    private final @NotNull Instant timestamp;

    private final @NotNull String canonicalQuery;

    private final @NotNull String clientId;

    private final @NotNull String method;

    private final @NotNull String nonce;

    private final @NotNull String path;

    private final byte @NotNull [] body;

    /** 创建已经规范化的 OpenAPI HMAC 请求。 */
    public OpenApiHmacCanonicalRequest(
            @NotNull String clientId,
            @NotNull String method,
            @NotNull String path,
            @NotNull String canonicalQuery,
            byte @NotNull [] body,
            @NotNull Instant timestamp,
            @NotNull String nonce) {
        this.clientId = TextUtils.requireText(clientId, "Open Client ID");
        this.method = TextUtils.requireText(method, "OpenAPI HMAC HTTP method").toUpperCase(Locale.ROOT);
        this.path = TextUtils.requireText(path, "OpenAPI HMAC HTTP path");
        this.canonicalQuery = Objects.requireNonNull(canonicalQuery, "OpenAPI HMAC query 不能为空");
        this.body = Objects.requireNonNull(body, "OpenAPI HMAC body 不能为空").clone();
        this.timestamp = Objects.requireNonNull(timestamp, "OpenAPI HMAC timestamp 不能为空");
        this.nonce = TextUtils.requireText(nonce, "OpenAPI HMAC nonce");
        if (!this.path.startsWith("/")
                || this.path.contains("//")
                || this.path.indexOf('\n') >= 0
                || this.canonicalQuery.indexOf('\n') >= 0
                || this.nonce.length() < 16
                || this.nonce.length() > 128
                || this.nonce.chars().anyMatch(value -> value < 0x21 || value > 0x7e)) {
            throw new IllegalArgumentException("OpenAPI HMAC path、query 或 nonce 格式不合法");
        }
    }

    /** @return Client ID */
    public @NotNull String clientId() {
        return clientId;
    }

    /** @return 客户端声明的请求时间 */
    public @NotNull Instant timestamp() {
        return timestamp;
    }

    /** @return 一次性 nonce */
    public @NotNull String nonce() {
        return nonce;
    }

    /** @return 规范请求的 UTF-8 字节序列 */
    public byte @NotNull [] canonicalBytes() {
        String value = "OPENAPI-HMAC-SHA256\n"
                + clientId + '\n'
                + method + '\n'
                + path + '\n'
                + canonicalQuery + '\n'
                + DigestUtil.sha256Hex(body) + '\n'
                + timestamp.getEpochSecond() + '\n'
                + nonce;
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
