package com.sca4d.authentication;

import com.sca4d.common.auth.AccountScopeType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

/** Flowable ProcessInstance 当前等待的任务及其认证变量。 */
@Data
@Accessors(chain = true)
public class AuthenticationProcess {
    private String authenticationId;
    private AuthenticationPurpose purpose;
    private String activityId;
    private AccountScopeType scopeType;
    private Long scopeId;
    private String browserHash;
    private String deviceId;
    private String terminalName;
    private Long subjectId;
    private Long accountId;
    private AuthMethodType claimedMethodType;
    private AuthMethodType primaryMethodType;
    private String primaryProofId;
    private Long primaryProofVersion;
    private boolean secondFactorRequired;
    private AuthMethodType secondFactorMethodType;
    private String secondFactorProofId;
    private Long secondFactorProofVersion;
    private Instant createdAt;
    private Instant expiresAt;
}
