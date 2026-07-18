package com.sca4d.authentication.email;

import org.jetbrains.annotations.NotNull;

/** 用户向当前认证提交的邮件验证码。 */
public final class EmailCodeAuthenticationProof {

    private final String authenticationId;
    private final String challengeId;
    private final String code;

    /**
     * @param authenticationId Challenge 所属认证
     * @param challengeId 邮件 Challenge UUID
     * @param code 用户提交的一次性验证码
     */
    public EmailCodeAuthenticationProof(
            @NotNull String authenticationId,
            @NotNull String challengeId,
            @NotNull String code) {
        this.authenticationId = authenticationId;
        this.challengeId = challengeId;
        this.code = code;
    }

    /** @return 当前验证码所属认证 */
    public @NotNull String getAuthenticationId() {
        return authenticationId;
    }

    /** @return 当前验证码消费的邮件 Challenge UUID */
    public @NotNull String getChallengeId() {
        return challengeId;
    }

    /** @return 只在当前验证调用中存在的一次性验证码 */
    public @NotNull String getCode() {
        return code;
    }

}
