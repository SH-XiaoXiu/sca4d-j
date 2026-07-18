package com.sca4d.authentication.passkey;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Instant;

/** Redis 中短期保存的 Passkey assertion、所属认证、浏览器摘要和 WebAuthn request JSON。 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PasskeyAssertion {
    private String assertionId;
    private PasskeyAssertionState state;
    private Long version;
    private String authenticationId;
    private String browserHash;
    private String requestJson;
    private Instant createdAt;
    private Instant expiresAt;

    /** 将 PENDING assertion 标记为已消费并推进 Redis CAS 版本。 */
    public void consume() {
        if (state != PasskeyAssertionState.PENDING) {
            throw new IllegalStateException("Passkey assertion 已经消费");
        }
        state = PasskeyAssertionState.CONSUMED;
        version++;
    }
}
