package com.sca4d.identity.ldap;

import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.AuthenticationScope;
import com.sca4d.authentication.AuthenticationScopeService;
import com.sca4d.authentication.audit.AuthenticationAuditOperations;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.exception.BizException;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.external.ExternalIdentityAuthenticationProof;
import com.sca4d.identity.external.ExternalIdentityAuthenticationService;
import com.sca4d.identity.external.ExternalIdentityProfile;
import com.sca4d.identity.external.ExternalIdentityVerification;
import com.sca4d.identity.repository.IdentityProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

/** 使用 LDAP 搜索和用户 bind 验证目录账号与密码。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultLdapAuthenticationService implements LdapAuthenticationService {

    private final AuthenticationProcessService authentications;
    private final AuthenticationScopeService authenticationScopes;
    private final IdentityProviderRepository providers;
    private final LdapProviderRepository ldapProviders;
    private final LdapDirectoryClient directory;
    private final ExternalIdentityAuthenticationService externalIdentityAuthentication;

    @Override
    public @NotNull List<LdapAvailableProvider> providers(
            @NotNull AccountScopeType scopeType, @Nullable String tenantCode) {
        AuthenticationScope authenticationScope = authenticationScopes.resolve(scopeType, tenantCode);
        IdentityProviderScope scope = new IdentityProviderScope(
                authenticationScope.getScopeType(), authenticationScope.getScopeId());
        return providers.findProviders(scope).stream()
                .filter(provider -> provider.getProtocol() == IdentityProviderProtocol.LDAP)
                .filter(provider -> provider.getStatus() == IdentityProviderStatus.ACTIVE)
                .map(provider -> new LdapAvailableProvider(provider.getCode(), provider.getName()))
                .toList();
    }

    @Override
    @Audit(module = AuthenticationAuditOperations.LOGIN, action = AuthenticationAuditOperations.LDAP)
    public @NotNull AuthenticationProcess authenticate(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull String providerCode,
            @NotNull String username,
            @NotNull String password) {
        AuthenticationProcess current = authentications.requireProof(
                authenticationId, browserHash, AuthMethodType.LDAP);
        IdentityProviderScope scope = new IdentityProviderScope(
                current.getScopeType(), current.getScopeId());
        IdentityProvider provider = providers.findProvider(scope, providerCode);
        if (provider == null
                || provider.getProtocol() != IdentityProviderProtocol.LDAP
                || provider.getStatus() != IdentityProviderStatus.ACTIVE) {
            throw new BizException(IdentityProviderErrorCode.LDAP_PROVIDER_UNAVAILABLE);
        }
        LdapConnection connection = ldapProviders.findConnection(
                provider.getProviderId(), provider.getVersion());
        if (connection == null) {
            throw new BizException(IdentityProviderErrorCode.LDAP_PROVIDER_UNAVAILABLE);
        }

        ExternalIdentityProfile profile = directory.authenticate(connection, username, password);
        // LDAP bind 成功后才选定 LDAP，目录连接或密码错误时仍可重新提交或改用其他认证方式。
        AuthenticationProcess claimed = authentications.claimPrimary(
                authenticationId, browserHash, AuthMethodType.LDAP);
        ExternalIdentityVerification verification = externalIdentityAuthentication.authenticate(
                claimed, provider, profile);
        AuthenticationProcess verified = authentications.proof(
                authenticationId,
                browserHash,
                AuthMethodType.LDAP,
                ExternalIdentityAuthenticationProof.class,
                new ExternalIdentityAuthenticationProof(
                        verification.getIdentityId(), verification.getIdentityVersion()));
        log.info(
                "LDAP 账号验证通过 | authenticationId={} providerId={} identityId={} subjectId={} accountId={} activityId={}",
                verified.getAuthenticationId(),
                provider.getProviderId(),
                verification.getIdentityId(),
                verification.getSubject().getSubjectId(),
                verification.getAccount().getAccountId(),
                verified.getActivityId());
        return verified;
    }
}
