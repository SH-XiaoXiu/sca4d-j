package com.sca4d.identity.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.AuthenticationPurpose;
import com.sca4d.authentication.AuthenticationScopeService;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.external.ExternalIdentityAuthenticationProof;
import com.sca4d.identity.external.ExternalIdentityAuthenticationService;
import com.sca4d.identity.external.ExternalIdentityProfile;
import com.sca4d.identity.external.ExternalIdentityVerification;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import com.sca4d.identity.repository.IdentityProviderRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/** 验证 LDAP Provider 选择、用户 bind 资料和认证任务提交。 */
class DefaultLdapAuthenticationServiceTest {

    @Test
    void shouldAuthenticateDirectoryIdentityAndAdvanceFlow() {
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
        LdapProviderRepository ldapProviders = mock(LdapProviderRepository.class);
        LdapDirectoryClient directory = mock(LdapDirectoryClient.class);
        ExternalIdentityAuthenticationService externalIdentityAuthentication = mock(ExternalIdentityAuthenticationService.class);
        AuthenticationProcess pending = pendingFlow();
        AuthenticationProcess claimed = pendingFlow()
                .setClaimedMethodType(AuthMethodType.LDAP);
        AuthenticationProcess verified = pendingFlow()
                .setActivityId("completion")
                .setSubjectId(20L)
                .setAccountId(30L)
                .setPrimaryMethodType(AuthMethodType.LDAP);
        IdentityProvider provider = provider();
        LdapConnection connection = connection();
        ExternalIdentityProfile profile = new ExternalIdentityProfile(
                "entry-1", "Alice", "alice@example.com", true, null);
        ExternalIdentityVerification identity = new ExternalIdentityVerification(
                new Subject(20L, "Alice"),
                new Account(30L, 20L, AccountScopeType.GLOBAL, null, false),
                200L,
                4L);
        when(flows.requireProof(pending.getAuthenticationId(), "browser-hash", AuthMethodType.LDAP))
                .thenReturn(pending);
        when(providers.findProvider(IdentityProviderScope.global(), "corporate"))
                .thenReturn(provider);
        when(ldapProviders.findConnection(100L, 3L)).thenReturn(connection);
        when(flows.claimPrimary(pending.getAuthenticationId(), "browser-hash", AuthMethodType.LDAP))
                .thenReturn(claimed);
        when(directory.authenticate(connection, "alice", "directory-password"))
                .thenReturn(profile);
        when(externalIdentityAuthentication.authenticate(claimed, provider, profile))
                .thenReturn(identity);
        when(flows.proof(
                eq(pending.getAuthenticationId()),
                eq("browser-hash"),
                eq(AuthMethodType.LDAP),
                eq(ExternalIdentityAuthenticationProof.class),
                any(ExternalIdentityAuthenticationProof.class)))
                .thenReturn(verified);
        DefaultLdapAuthenticationService service = new DefaultLdapAuthenticationService(
                flows,
                mock(AuthenticationScopeService.class),
                providers,
                ldapProviders,
                directory,
                externalIdentityAuthentication);

        AuthenticationProcess result = service.authenticate(
                pending.getAuthenticationId(),
                "browser-hash",
                "corporate",
                "alice",
                "directory-password");

        assertThat(result).isSameAs(verified);
        verify(directory).authenticate(connection, "alice", "directory-password");
        verify(externalIdentityAuthentication).authenticate(claimed, provider, profile);
    }

    @Test
    void shouldKeepFlowUnclaimedWhenDirectoryRejectsPassword() {
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
        LdapProviderRepository ldapProviders = mock(LdapProviderRepository.class);
        LdapDirectoryClient directory = mock(LdapDirectoryClient.class);
        AuthenticationProcess pending = pendingFlow();
        IdentityProvider provider = provider();
        LdapConnection connection = connection();
        when(flows.requireProof(pending.getAuthenticationId(), "browser-hash", AuthMethodType.LDAP))
                .thenReturn(pending);
        when(providers.findProvider(IdentityProviderScope.global(), "corporate"))
                .thenReturn(provider);
        when(ldapProviders.findConnection(100L, 3L)).thenReturn(connection);
        when(directory.authenticate(connection, "alice", "wrong-password"))
                .thenThrow(new BizException(IdentityProviderErrorCode.LDAP_CREDENTIAL_INVALID));
        DefaultLdapAuthenticationService service = new DefaultLdapAuthenticationService(
                flows,
                mock(AuthenticationScopeService.class),
                providers,
                ldapProviders,
                directory,
                mock(ExternalIdentityAuthenticationService.class));

        assertThatThrownBy(() -> service.authenticate(
                pending.getAuthenticationId(),
                "browser-hash",
                "corporate",
                "alice",
                "wrong-password"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.LDAP_CREDENTIAL_INVALID));
        verify(flows, never()).claimPrimary(any(), any(), any());
    }

    /** @return 等待 LDAP 账号密码的 GLOBAL 认证 */
    private AuthenticationProcess pendingFlow() {
        return new AuthenticationProcess()
                .setAuthenticationId("11111111-1111-1111-1111-111111111111")
                .setPurpose(AuthenticationPurpose.LOGIN)
                .setActivityId("primary-proof")
                .setScopeType(AccountScopeType.GLOBAL)
                .setCreatedAt(Instant.now())
                .setExpiresAt(Instant.now().plusSeconds(300));
    }

    /** @return ACTIVE GLOBAL LDAP Provider。 */
    private IdentityProvider provider() {
        return new IdentityProvider(
                100L,
                IdentityProviderScope.global(),
                "corporate",
                "Corporate Directory",
                IdentityProviderProtocol.LDAP,
                IdentityProviderRegistrationPolicy.JIT,
                false,
                IdentityProviderStatus.ACTIVE,
                3L);
    }

    /** @return LDAPS 服务账号搜索配置。 */
    private LdapConnection connection() {
        return new LdapConnection(
                100L,
                "ldaps://directory.example.com:636",
                "dc=example,dc=com",
                "cn=reader,dc=example,dc=com",
                new EncryptedSecret("current", new byte[12], new byte[] {1}),
                "ou=people",
                "(uid={0})",
                "entryUUID",
                "displayName",
                "mail",
                LdapTransportSecurity.LDAPS,
                3L);
    }
}
