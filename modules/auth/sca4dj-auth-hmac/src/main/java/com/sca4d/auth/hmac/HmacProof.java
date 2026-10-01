package com.sca4d.auth.hmac;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存一次 MAC 认证所需的时间、nonce、canonical bytes 和签名。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class HmacProof {

    private static final int HMAC_SHA256_BASE64URL_LENGTH = 43;

    private static final int MAX_NONCE_LENGTH = 128;

    private static final int MAX_CANONICAL_BYTES = 64 * 1024;

    private final @NotNull Instant timestamp;

    private final @NotNull String nonce;

    private final @NotNull String signature;

    private final byte @NotNull [] canonicalBytes;

    /** 创建具体协议已经规范化的 MAC Proof。 */
    public HmacProof(
            @NotNull Instant timestamp,
            @NotNull String nonce,
            byte @NotNull [] canonicalBytes,
            @NotNull String signature) {
        this.timestamp = Objects.requireNonNull(timestamp, "HMAC timestamp 不能为空");
        this.nonce = TextUtils.requireText(nonce, "HMAC nonce");
        if (this.nonce.length() > MAX_NONCE_LENGTH) {
            throw new IllegalArgumentException("HMAC nonce 不能超过 128 个字符");
        }
        this.canonicalBytes = Objects.requireNonNull(canonicalBytes, "HMAC canonical bytes 不能为空").clone();
        if (this.canonicalBytes.length == 0 || this.canonicalBytes.length > MAX_CANONICAL_BYTES) {
            throw new IllegalArgumentException("HMAC canonical bytes 长度必须在 1 到 65536 字节之间");
        }
        this.signature = TextUtils.requireText(signature, "HMAC signature");
        if (this.signature.length() != HMAC_SHA256_BASE64URL_LENGTH) {
            throw new IllegalArgumentException("HMAC-SHA256 signature 必须是 43 个字符的无填充 Base64URL");
        }
    }

    /** @return 客户端声明的请求时间 */
    public @NotNull Instant timestamp() {
        return timestamp;
    }

    /** @return 当前 Account 时间窗内必须唯一的 nonce */
    public @NotNull String nonce() {
        return nonce;
    }

    /** @return 具体协议按固定规则生成的字节副本 */
    public byte @NotNull [] canonicalBytes() {
        return canonicalBytes.clone();
    }

    /** @return 无填充 Base64URL MAC-SHA256 */
    public @NotNull String signature() {
        return signature;
    }

    @Override
    public @NotNull String toString() {
        return "HmacProof[timestamp=" + timestamp + ", nonce=protected, canonicalBytes=protected, signature=protected]";
    }

}
