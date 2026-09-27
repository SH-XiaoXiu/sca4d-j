package com.sca4d.app.server.iot;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存一次仍在有效窗口内的 Device 创建幂等请求和加密响应。 */
public class DeviceCreationRequestRecord {

    private final @NotNull String idempotencyKey;

    private final @NotNull String requestHash;

    private final @NotNull String responseKeyId;

    private final byte @NotNull [] responseCiphertext;

    private final @NotNull Instant expiresAt;

    /** 创建 Mapper 返回的 Device 创建幂等记录。 */
    public DeviceCreationRequestRecord(
            @NotNull String idempotencyKey,
            @NotNull String requestHash,
            @NotNull String responseKeyId,
            byte @NotNull [] responseCiphertext,
            @NotNull Instant expiresAt) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        this.requestHash = Objects.requireNonNull(requestHash, "请求摘要不能为空");
        this.responseKeyId = Objects.requireNonNull(responseKeyId, "响应密钥标识不能为空");
        this.responseCiphertext = Objects.requireNonNull(responseCiphertext, "响应密文不能为空").clone();
        this.expiresAt = Objects.requireNonNull(expiresAt, "幂等结果过期时间不能为空");
    }

    /** @return HTTP Idempotency-Key */
    public @NotNull String idempotencyKey() {
        return idempotencyKey;
    }

    /** @return 规范请求的 SHA-256 小写十六进制摘要 */
    public @NotNull String requestHash() {
        return requestHash;
    }

    /** @return 加密响应使用的 SecretKeyring 密钥标识 */
    public @NotNull String responseKeyId() {
        return responseKeyId;
    }

    /** @return 加密响应的防御性副本 */
    public byte @NotNull [] responseCiphertext() {
        return responseCiphertext.clone();
    }

    /** @return 超过该时间后不再重放一次性 Secret */
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }
}
