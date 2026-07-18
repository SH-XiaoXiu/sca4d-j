package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.config.properties.Sca4djIdentityProviderProperties;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.external.ExternalIdentityAccountService;
import com.sca4d.identity.ldap.LdapAuthenticationService;
import com.sca4d.identity.ldap.LdapAvailableProvider;
import com.sca4d.identity.ldap.request.LdapProofRequest;
import com.sca4d.identity.oauth2.OAuth2AuthenticationService;
import com.sca4d.identity.oauth2.OAuth2AuthorizationStart;
import com.sca4d.identity.oauth2.OAuth2AvailableProvider;
import com.sca4d.identity.oauth2.request.OAuth2AuthorizationCreateRequest;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/** 验证 OAuth2、LDAP 和 ExternalIdentity HTTP 资源契约。 */
class ExternalAuthenticationControllerTest {

    @Test
    void shouldListAvailableProvidersFromOwnedAccount() throws Exception {
        ExternalIdentityAccountService service = mock(ExternalIdentityAccountService.class);
        Subject subject = new Subject(10L, "Alice");
        when(service.providers(10L, 20L))
                .thenReturn(List.of(new IdentityProvider(
                        30L,
                        IdentityProviderScope.global(),
                        "corporate",
                        "Corporate",
                        IdentityProviderProtocol.OIDC,
                        IdentityProviderRegistrationPolicy.DISABLED,
                        false,
                        IdentityProviderStatus.ACTIVE,
                        2L)));
        ExternalIdentityController controller = new ExternalIdentityController(
                service, mock(AuthenticationBrowser.class));

        var response = controller.providers(20L, subject);

        assertThat(ExternalIdentityController.class
                .getDeclaredMethod("providers", Long.class, Subject.class)
                .getAnnotation(GetMapping.class)
                .value())
                .containsExactly("/providers");
        assertThat(response.getData()).singleElement().satisfies(provider -> {
            assertThat(provider.getCode()).isEqualTo("corporate");
            assertThat(provider.getName()).isEqualTo("Corporate");
            assertThat(provider.getProtocol()).isEqualTo(IdentityProviderProtocol.OIDC);
        });
        verify(service).providers(10L, 20L);
    }

    @Test
    void shouldCreateOAuth2AuthorizationAndListProviders() throws Exception {
        OAuth2AuthenticationService oauth2 = mock(OAuth2AuthenticationService.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(oauth2.providers(AccountScopeType.TENANT, "acme"))
                .thenReturn(List.of(new OAuth2AvailableProvider("corporate", "Corporate")));
        when(browser.require(servletRequest)).thenReturn("browser-hash");
        when(oauth2.authorize("flow-1", "browser-hash", "corporate"))
                .thenReturn(new OAuth2AuthorizationStart(
                        "https://idp.example.com/authorize?state=state-1",
                        Instant.parse("2026-07-17T05:30:00Z")));
        OAuth2AuthenticationController controller = new OAuth2AuthenticationController(
                oauth2, browser, new Sca4djIdentityProviderProperties());
        OAuth2AuthorizationCreateRequest request = new OAuth2AuthorizationCreateRequest();
        request.setProviderCode("corporate");

        var providers = controller.providers(AccountScopeType.TENANT, "acme");
        var authorization = controller.createAuthorization("flow-1", request, servletRequest);

        assertThat(OAuth2AuthenticationController.class
                .getDeclaredMethod("providers", AccountScopeType.class, String.class)
                .getAnnotation(GetMapping.class)
                .value())
                .containsExactly("/auth/oauth2/providers");
        assertThat(OAuth2AuthenticationController.class
                .getDeclaredMethod(
                        "createAuthorization",
                        String.class,
                        OAuth2AuthorizationCreateRequest.class,
                        HttpServletRequest.class)
                .getAnnotation(PostMapping.class)
                .value())
                .containsExactly("/authentications/{authenticationId}/oauth2-authorizations");
        assertThat(providers.getData()).singleElement().satisfies(provider -> {
            assertThat(provider.getCode()).isEqualTo("corporate");
            assertThat(provider.getName()).isEqualTo("Corporate");
        });
        assertThat(authorization.responseStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(authorization.getData().getAuthorizationUri())
                .isEqualTo("https://idp.example.com/authorize?state=state-1");
        verify(oauth2).authorize("flow-1", "browser-hash", "corporate");
    }

    @Test
    void shouldRedirectOAuth2CallbackWithoutUpstreamCredentials() {
        OAuth2AuthenticationService oauth2 = mock(OAuth2AuthenticationService.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        Sca4djIdentityProviderProperties properties = new Sca4djIdentityProviderProperties();
        properties.getOauth2().setCompletionUri(URI.create("https://app.example.com/login"));
        when(browser.require(request)).thenReturn("browser-hash");
        when(oauth2.callback("state-1", "code-1", null, "browser-hash"))
                .thenReturn(new AuthenticationProcess()
                        .setAuthenticationId("flow-1")
                        .setActivityId("completion"));
        OAuth2AuthenticationController controller = new OAuth2AuthenticationController(
                oauth2, browser, properties);

        var response = controller.callback("state-1", "code-1", null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation())
                .hasToString("https://app.example.com/login?protocol=OAUTH2&authenticationId=flow-1&activityId=completion");
        assertThat(response.getHeaders().getLocation().toString())
                .doesNotContain("state-1", "code-1", "access-token");
    }

    @Test
    void shouldHideOAuth2CallbackBusinessFailure() {
        OAuth2AuthenticationService oauth2 = mock(OAuth2AuthenticationService.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        Sca4djIdentityProviderProperties properties = new Sca4djIdentityProviderProperties();
        properties.getOauth2().setCompletionUri(URI.create("https://app.example.com/login"));
        when(browser.require(request)).thenReturn("browser-hash");
        when(oauth2.callback("state-1", null, "access_denied", "browser-hash"))
                .thenThrow(new BizException(IdentityProviderErrorCode.OAUTH2_AUTHORIZATION_REJECTED));
        OAuth2AuthenticationController controller = new OAuth2AuthenticationController(
                oauth2, browser, properties);

        var response = controller.callback("state-1", null, "access_denied", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation())
                .hasToString("https://app.example.com/login?oauth2Failed=true");
        assertThat(response.getHeaders().getLocation().toString())
                .doesNotContain("state-1", "access_denied");
    }

    @Test
    void shouldRejectOAuth2CallbackBeforeConsumingStateWhenCompletionUriIsMissing() {
        OAuth2AuthenticationService oauth2 = mock(OAuth2AuthenticationService.class);
        OAuth2AuthenticationController controller = new OAuth2AuthenticationController(
                oauth2, mock(AuthenticationBrowser.class), new Sca4djIdentityProviderProperties());

        assertThatThrownBy(() -> controller.callback(
                "state-1", "code-1", null, mock(HttpServletRequest.class)))
                .isInstanceOf(InternalServiceException.class)
                .hasMessage("OAuth2 completion-uri 未配置");
        verifyNoInteractions(oauth2);
    }

    @Test
    void shouldSubmitBrowserBoundLdapProof() throws Exception {
        LdapAuthenticationService ldap = mock(LdapAuthenticationService.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(ldap.providers(AccountScopeType.GLOBAL, null))
                .thenReturn(List.of(new LdapAvailableProvider("directory", "Directory")));
        when(browser.require(servletRequest)).thenReturn("browser-hash");
        when(ldap.authenticate("flow-1", "browser-hash", "directory", "alice", "secret"))
                .thenReturn(new AuthenticationProcess()
                        .setAuthenticationId("flow-1")
                        .setActivityId("completion"));
        LdapAuthenticationController controller = new LdapAuthenticationController(ldap, browser);
        LdapProofRequest request = new LdapProofRequest();
        request.setProviderCode("directory");
        request.setUsername("alice");
        request.setPassword("secret");

        var providers = controller.providers(AccountScopeType.GLOBAL, null);
        var proof = controller.proof("flow-1", request, servletRequest);

        assertThat(LdapAuthenticationController.class
                .getDeclaredMethod(
                        "proof", String.class, LdapProofRequest.class, HttpServletRequest.class)
                .getAnnotation(PostMapping.class)
                .value())
                .containsExactly("/authentications/{authenticationId}/ldap-proofs");
        assertThat(providers.getData()).singleElement().satisfies(provider -> assertThat(provider.getCode()).isEqualTo("directory"));
        assertThat(proof.getData().getActivityId()).isEqualTo("completion");
        verify(ldap).authenticate("flow-1", "browser-hash", "directory", "alice", "secret");
    }

    @Test
    void shouldPassStepUpAndStrongVersionWhenDeletingExternalIdentity() throws Exception {
        ExternalIdentityAccountService service = mock(ExternalIdentityAccountService.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        Subject subject = mock(Subject.class);
        Account account = mock(Account.class);
        when(service.identities(10L, 20L)).thenReturn(List.of());
        when(subject.getSubjectId()).thenReturn(10L);
        when(browser.require(request)).thenReturn("browser-hash");
        ExternalIdentityController controller = new ExternalIdentityController(service, browser);

        var identities = controller.identities(20L, subject);
        var response = controller.delete(
                20L, 30L, "\"4\"", "step-up-flow", subject, account, request);

        assertThat(ExternalIdentityController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/me/accounts/{accountId}/external-identities");
        assertThat(ExternalIdentityController.class
                .getDeclaredMethod(
                        "delete",
                        Long.class,
                        Long.class,
                        String.class,
                        String.class,
                        Subject.class,
                        Account.class,
                        HttpServletRequest.class)
                .getAnnotation(DeleteMapping.class)
                .value())
                .containsExactly("/{identityId}");
        assertThat(identities.getData()).isEmpty();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(
                subject, account, 20L, 30L, 4L, "step-up-flow", "browser-hash");
    }
}
