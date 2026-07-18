package com.sca4d.authentication.email;

import com.sca4d.common.auth.AccountScopeType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.Instant;

/** Redis 中短期保存的邮件验证码摘要、目标 Account 和单次消费状态。 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class EmailCodeChallenge {

    private String challengeId;
    private String authenticationId;
    private Long subjectId;
    private Long accountId;
    private AccountScopeType scopeType;
    private Long scopeId;
    private Long contactId;
    private Long contactVersion;
    private Long verifiedContactVersion;
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String codeHash;
    private EmailCodeChallengeState state;
    private Long version;
    private Integer attemptsRemaining;
    private Instant expiresAt;
}
