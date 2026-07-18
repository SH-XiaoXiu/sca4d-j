package com.sca4d.authentication;

import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.exception.BizException;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 创建 Session 或执行敏感操作前，重新检查已经通过的凭据及其版本。 */
@Component
public class AuthenticationProofReviewer {
    private final AuthMethodCatalog authMethods;

    public AuthenticationProofReviewer(AuthMethodCatalog authMethods) {
        this.authMethods = authMethods;
    }

    public @NotNull AuthMethodVerification requireCurrent(@NotNull AuthenticationProcess process) {
        if (process.getSubjectId() == null
                || process.getAccountId() == null
                || process.getPrimaryMethodType() == null
                || process.getPrimaryProofId() == null
                || process.getPrimaryProofVersion() == null) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }
        AuthMethodVerification proof = new AuthMethodVerification(
                process.getPrimaryMethodType(),
                new Subject(process.getSubjectId(), null),
                new Account(
                        process.getAccountId(),
                        process.getSubjectId(),
                        process.getScopeType(),
                        process.getScopeId(),
                        process.isSecondFactorRequired()),
                process.getPrimaryProofId(),
                process.getPrimaryProofVersion());
        AuthMethodVerification current = authMethods.require(process.getPrimaryMethodType()).requireCurrent(proof);
        if (current.getMethodType() != process.getPrimaryMethodType()
                || !process.getSubjectId().equals(current.getSubject().getSubjectId())
                || !process.getAccountId().equals(current.getAccount().getAccountId())
                || current.getAccount().getScopeType() != process.getScopeType()
                || !Objects.equals(current.getAccount().getScopeId(), process.getScopeId())
                || !process.getPrimaryProofId().equals(current.getProofId())
                || !process.getPrimaryProofVersion().equals(current.getProofVersion())
                || process.isSecondFactorRequired() != current.getAccount().isSecondFactorRequired()) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }
        if (!process.isSecondFactorRequired()) {
            return current;
        }
        if (process.getSecondFactorMethodType() == null
                || process.getSecondFactorProofId() == null
                || process.getSecondFactorProofVersion() == null) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }
        AuthMethodVerification secondFactor = new AuthMethodVerification(
                process.getSecondFactorMethodType(),
                current.getSubject(),
                current.getAccount(),
                process.getSecondFactorProofId(),
                process.getSecondFactorProofVersion());
        AuthMethodVerification currentSecondFactor = authMethods.require(process.getSecondFactorMethodType())
                .requireCurrent(secondFactor);
        if (currentSecondFactor.getMethodType() != process.getSecondFactorMethodType()
                || !process.getSubjectId().equals(currentSecondFactor.getSubject().getSubjectId())
                || !process.getAccountId().equals(currentSecondFactor.getAccount().getAccountId())
                || !process.getSecondFactorProofId().equals(currentSecondFactor.getProofId())
                || !process.getSecondFactorProofVersion().equals(currentSecondFactor.getProofVersion())) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }
        return current;
    }
}
