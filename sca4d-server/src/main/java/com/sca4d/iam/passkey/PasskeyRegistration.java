package com.sca4d.iam.passkey;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Instant;

/** Redis 中短期保存的 Passkey registration 归属、浏览器绑定和 Yubico request JSON。 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PasskeyRegistration {
    private String registrationId;
    private PasskeyRegistrationState state;
    private Long version;
    private Long subjectId;
    private Long accountId;
    private String browserHash;
    private String requestJson;
    private String displayName;
    private Instant createdAt;
    private Instant expiresAt;

    /** 将 PENDING registration 标记为已消费并推进 Redis CAS 版本。 */
    public void consume() {
        if (state != PasskeyRegistrationState.PENDING) {
            throw new IllegalStateException("Passkey registration 已经消费");
        }
        state = PasskeyRegistrationState.CONSUMED;
        version++;
    }
}
