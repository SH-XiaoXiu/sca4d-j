package com.sca4d.identity.external;

import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.AuthenticationPurpose;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.iam.identifier.AccountIdentifierService;
import com.sca4d.iam.identifier.AccountIdentifierType;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

/** 复用 IdentityProvider 的邮箱关联和 JIT 规则处理 OIDC、OAuth2 与 LDAP 上游身份。 */
@Service
@RequiredArgsConstructor
public class DefaultExternalIdentityAuthenticationService implements ExternalIdentityAuthenticationService {

    private final ExternalIdentityService externalIdentities;
    private final ExternalIdentityRegistrationService registrations;
    private final AccountIdentifierService accountIdentifiers;

    @Override
    public @NotNull ExternalIdentityVerification authenticate(
            @NotNull AuthenticationProcess authentication,
            @NotNull IdentityProvider provider,
            @NotNull ExternalIdentityProfile profile) {
        IdentityProviderScope scope = provider.getScope();
        if (authentication.getPurpose() == AuthenticationPurpose.LINKING) {
            if (authentication.getAccountId() == null) {
                throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE);
            }
            return externalIdentities.link(
                    scope, provider.getProviderId(), authentication.getAccountId(), profile);
        }

        ExternalIdentityVerification verification = externalIdentities.authenticate(
                scope, provider.getProviderId(), profile);
        if (verification == null
                && Boolean.TRUE.equals(provider.getEmailAutoLink())
                && Boolean.TRUE.equals(profile.getEmailVerified())
                && profile.getEmail() != null) {
            IamAccountIdentifierEntity identifier = accountIdentifiers.findActive(
                    scope.getScopeType(),
                    scope.getScopeId(),
                    AccountIdentifierType.EMAIL,
                    profile.getEmail());
            if (identifier != null) {
                verification = externalIdentities.link(
                        scope,
                        provider.getProviderId(),
                        identifier.getAccountId(),
                        profile);
            }
        }
        if (verification != null) {
            return verification;
        }
        if (provider.getRegistrationPolicy() != IdentityProviderRegistrationPolicy.JIT) {
            throw new BizException(IdentityProviderErrorCode.EXTERNAL_REGISTRATION_DISABLED);
        }
        try {
            return registrations.register(scope, provider.getProviderId(), profile);
        } catch (BizException exception) {
            if (exception.getErrorCode() != IdentityProviderErrorCode.EXTERNAL_IDENTITY_ALREADY_LINKED) {
                throw exception;
            }
            // 并发 JIT 中只有一个事务创建绑定；其他请求重新读取同一 external subject。
            ExternalIdentityVerification concurrent = externalIdentities.authenticate(
                    scope, provider.getProviderId(), profile);
            if (concurrent == null) {
                throw exception;
            }
            return concurrent;
        }
    }
}
