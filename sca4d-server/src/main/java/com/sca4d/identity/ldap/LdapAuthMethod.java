package com.sca4d.identity.ldap;

import com.sca4d.authentication.AuthMethod;
import com.sca4d.authentication.AuthMethodCapability;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthMethodVerification;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.common.exception.BizException;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.external.ExternalIdentityAuthenticationProof;
import com.sca4d.identity.external.ExternalIdentityService;
import com.sca4d.identity.external.ExternalIdentityVerification;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/** 把已完成 LDAP bind 的 ExternalIdentity ID 与版本转换为认证结果。 */
@Component
@RequiredArgsConstructor
public class LdapAuthMethod implements AuthMethod<ExternalIdentityAuthenticationProof> {

    private static final Set<AuthMethodCapability> CAPABILITIES = Set.of(
            AuthMethodCapability.LOGIN,
            AuthMethodCapability.REGISTRATION,
            AuthMethodCapability.LINKING);

    private final ExternalIdentityService externalIdentities;

    @Override
    public @NotNull AuthMethodType type() {
        return AuthMethodType.LDAP;
    }

    @Override
    public @NotNull Class<ExternalIdentityAuthenticationProof> proofClass() {
        return ExternalIdentityAuthenticationProof.class;
    }

    @Override
    public @NotNull Set<AuthMethodCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public @NotNull AuthMethodVerification verify(
            @NotNull AuthenticationProcess authentication,
            @NotNull ExternalIdentityAuthenticationProof proof) {
        ExternalIdentityVerification current = externalIdentities.requireCurrent(
                proof.getIdentityId(), proof.getIdentityVersion());
        if (current.getAccount().getScopeType() != authentication.getScopeType()
                || !Objects.equals(current.getAccount().getScopeId(), authentication.getScopeId())) {
            throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE);
        }
        return verification(current);
    }

    @Override
    public @NotNull AuthMethodVerification requireCurrent(
            @NotNull AuthMethodVerification verification) {
        return verification(externalIdentities.requireCurrent(
                Long.valueOf(verification.getProofId()), verification.getProofVersion()));
    }

    /** @return 当前仍可登录的 LDAP ExternalIdentity、Subject 和 Account */
    private @NotNull AuthMethodVerification verification(
            @NotNull ExternalIdentityVerification current) {
        return new AuthMethodVerification(
                type(),
                current.getSubject(),
                current.getAccount(),
                current.getIdentityId().toString(),
                current.getIdentityVersion());
    }
}
