package com.sca4d.iam.totp;

import com.sca4d.common.security.EncryptedSecret;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Base64;

/** Redis 中短期保存的 TOTP enrollment 归属、浏览器绑定和加密密钥。 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class TotpEnrollment {
    private String enrollmentId;
    private TotpEnrollmentState state;
    private Long version;
    private Long subjectId;
    private Long accountId;
    private String browserHash;
    private String secretKeyId;
    private String secretNonce;
    private String secretCiphertext;
    private Instant createdAt;
    private Instant expiresAt;

    /** @return 从 Base64 Redis 字段恢复且仍不包含明文的 AES-256-GCM 加密结果 */
    public @NotNull EncryptedSecret encryptedSecret() {
        return new EncryptedSecret(
                secretKeyId,
                Base64.getDecoder().decode(secretNonce),
                Base64.getDecoder().decode(secretCiphertext));
    }

    /** 将一次 PENDING enrollment 标记为已消费并推进 CAS 版本。 */
    public void consume() {
        if (state != TotpEnrollmentState.PENDING) {
            throw new IllegalStateException("TOTP enrollment 已经消费");
        }
        state = TotpEnrollmentState.CONSUMED;
        version++;
    }
}
