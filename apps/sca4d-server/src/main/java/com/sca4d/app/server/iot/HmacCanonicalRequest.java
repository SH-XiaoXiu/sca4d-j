package com.sca4d.app.server.iot;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** 按 Sca4d Server 的固定 HTTP 字段顺序生成 Device MAC canonical request。 */
public class HmacCanonicalRequest {

    private final @NotNull Instant timestamp;

    private final @NotNull String canonicalQuery;

    private final @NotNull String method;

    private final @NotNull String nonce;

    private final @NotNull String path;

    private final byte @NotNull [] body;

    private final long deviceId;

    /** 创建待签名 HTTP 请求；query 必须已经按名称和值排序并使用 RFC 3986 百分号编码。 */
    public HmacCanonicalRequest(
            long deviceId,
            @NotNull String method,
            @NotNull String path,
            @NotNull String canonicalQuery,
            byte @NotNull [] body,
            @NotNull Instant timestamp,
            @NotNull String nonce) {
        if (deviceId <= 0) {
            throw new IllegalArgumentException("HMAC Device 标识必须为正数");
        }
        this.deviceId = deviceId;
        this.method = TextUtils.requireText(method, "HMAC HTTP method").toUpperCase(Locale.ROOT);
        this.path = TextUtils.requireText(path, "HMAC HTTP path");
        this.canonicalQuery = Objects.requireNonNull(canonicalQuery, "HMAC canonical query 不能为空");
        this.body = Objects.requireNonNull(body, "HMAC HTTP body 不能为空").clone();
        this.timestamp = Objects.requireNonNull(timestamp, "HMAC timestamp 不能为空");
        this.nonce = TextUtils.requireText(nonce, "HMAC nonce");
        if (!path.startsWith("/")
                || path.indexOf('\n') >= 0
                || canonicalQuery.indexOf('\n') >= 0
                || nonce.length() < 16
                || nonce.length() > 128
                || nonce.chars().anyMatch(value -> value < 0x21 || value > 0x7e)) {
            throw new IllegalArgumentException("HMAC path、query 或 nonce 格式不合法");
        }
    }

    /** @return Device 标识 */
    @Contract(pure = true)
    public long deviceId() {
        return deviceId;
    }

    /** @return 请求时间 */
    @Contract(pure = true)
    public @NotNull Instant timestamp() {
        return timestamp;
    }

    /** @return 一次性随机 nonce */
    @Contract(pure = true)
    public @NotNull String nonce() {
        return nonce;
    }

    /** @return UTF-8 编码的固定 canonical request */
    @CheckReturnValue
    @Contract(pure = true)
    public byte @NotNull [] canonicalBytes() {
        String value = "IOT-HMAC-SHA256\n"
                + deviceId + '\n'
                + method + '\n'
                + path + '\n'
                + canonicalQuery + '\n'
                + DigestUtil.sha256Hex(body) + '\n'
                + timestamp.getEpochSecond() + '\n'
                + nonce;
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
