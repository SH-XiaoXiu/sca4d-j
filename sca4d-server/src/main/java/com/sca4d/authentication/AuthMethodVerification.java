package com.sca4d.authentication;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import org.jetbrains.annotations.NotNull;

/** AuthMethod 验证成功后返回的 Subject、Account、凭据或外部身份 ID 及版本号。 */
public final class AuthMethodVerification {

    private final AuthMethodType methodType;
    private final Subject subject;
    private final Account account;
    private final String proofId;
    private final Long proofVersion;

    public AuthMethodVerification(
            @NotNull AuthMethodType methodType,
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull String proofId,
            @NotNull Long proofVersion) {
        this.methodType = methodType;
        this.subject = subject;
        this.account = account;
        this.proofId = proofId;
        this.proofVersion = proofVersion;
    }

    public @NotNull AuthMethodType getMethodType() {
        return methodType;
    }

    public @NotNull Subject getSubject() {
        return subject;
    }

    public @NotNull Account getAccount() {
        return account;
    }

    public @NotNull String getProofId() {
        return proofId;
    }

    public @NotNull Long getProofVersion() {
        return proofVersion;
    }
}
