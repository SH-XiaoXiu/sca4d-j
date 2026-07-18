package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.config.properties.Sca4djIdentityProviderProperties;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.oidc.OidcAuthenticationService;
import com.sca4d.identity.oidc.OidcAuthorizationStart;
import com.sca4d.identity.oidc.OidcAvailableProvider;
import com.sca4d.identity.oidc.request.OidcAuthorizationCreateRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/** 验证 OIDC 登录入口、授权资源和 callback 重定向契约。 */
class OidcAuthenticationControllerTest {

    @Test
    void shouldListActiveProvidersForRequestedScope() throws Exception {
        OidcAuthenticationService oidc = mock(OidcAuthenticationService.class);
        when(oidc.providers(AccountScopeType.TENANT, "acme"))
                .thenReturn(List.of(new OidcAvailableProvider("corporate", "Corporate")));
        OidcAuthenticationController controller = new OidcAuthenticationController(
                oidc, mock(AuthenticationBrowser.class), new Sca4djIdentityProviderProperties());

        var response = controller.providers(AccountScopeType.TENANT, "acme");

        assertThat(OidcAuthenticationController.class
                .getDeclaredMethod("providers", AccountScopeType.class, String.class)
                .getAnnotation(GetMapping.class)
                .value())
                .containsExactly("/auth/oidc/providers");
        assertThat(response.getData()).singleElement().satisfies(provider -> {
            assertThat(provider.getCode()).isEqualTo("corporate");
            assertThat(provider.getName()).isEqualTo("Corporate");
        });
        verify(oidc).providers(AccountScopeType.TENANT, "acme");
    }

    @Test
    void shouldCreateBrowserBoundAuthorizationResource() throws Exception {
        OidcAuthenticationService oidc = mock(OidcAuthenticationService.class);
        AuthenticationBrowser authenticationBrowser = mock(AuthenticationBrowser.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        Instant expiresAt = Instant.parse("2026-07-17T03:05:00Z");
        String authorizationUri = "https://idp.example.com/authorize"
                + "?client_id=client-1&state=state-1&nonce=nonce-1&code_challenge=challenge-1";
        when(authenticationBrowser.require(servletRequest)).thenReturn("browser-hash");
        when(oidc.authorize("flow-1", "browser-hash", "corporate"))
                .thenReturn(new OidcAuthorizationStart(authorizationUri, expiresAt));
        OidcAuthorizationCreateRequest request = new OidcAuthorizationCreateRequest();
        request.setProviderCode("corporate");
        OidcAuthenticationController controller = new OidcAuthenticationController(
                oidc, authenticationBrowser, new Sca4djIdentityProviderProperties());

        var response = controller.createAuthorization("flow-1", request, servletRequest);

        assertThat(OidcAuthenticationController.class
                .getDeclaredMethod(
                        "createAuthorization",
                        String.class,
                        OidcAuthorizationCreateRequest.class,
                        HttpServletRequest.class)
                .getAnnotation(PostMapping.class)
                .value())
                .containsExactly("/authentications/{authenticationId}/oidc-authorizations");
        assertThat(response.responseStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.responseHeaders().getLocation()).isNull();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getAuthorizationUri()).isEqualTo(authorizationUri);
        assertThat(response.getData().getExpiresAt()).isEqualTo(expiresAt);
        verify(oidc).authorize("flow-1", "browser-hash", "corporate");
    }

    @Test
    void shouldRedirectToCompletionUriWithoutUpstreamCredentials() throws Exception {
        OidcAuthenticationService oidc = mock(OidcAuthenticationService.class);
        AuthenticationBrowser authenticationBrowser = mock(AuthenticationBrowser.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        Sca4djIdentityProviderProperties properties = new Sca4djIdentityProviderProperties();
        properties.getOidc().setCompletionUri(URI.create("https://app.example.com/login"));
        when(authenticationBrowser.require(servletRequest)).thenReturn("browser-hash");
        when(oidc.callback("state-1", "authorization-code", null, "browser-hash"))
                .thenReturn(new AuthenticationProcess()
                        .setAuthenticationId("flow-1")
                        .setActivityId("completion"));
        OidcAuthenticationController controller = new OidcAuthenticationController(
                oidc, authenticationBrowser, properties);

        var response = controller.callback(
                "state-1", "authorization-code", null, servletRequest);

        assertThat(OidcAuthenticationController.class
                .getDeclaredMethod(
                        "callback",
                        String.class,
                        String.class,
                        String.class,
                        HttpServletRequest.class)
                .getAnnotation(GetMapping.class)
                .value())
                .containsExactly("/auth/oidc/callback");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation())
                .hasToString("https://app.example.com/login?protocol=OIDC&authenticationId=flow-1&activityId=completion");
        assertThat(response.getHeaders().getLocation().toString())
                .doesNotContain("state-1", "authorization-code", "access-token", "id-token");
        verify(oidc).callback("state-1", "authorization-code", null, "browser-hash");
    }

    @Test
    void shouldRedirectBusinessFailureToCompletionUriWithoutErrorDetails() {
        OidcAuthenticationService oidc = mock(OidcAuthenticationService.class);
        AuthenticationBrowser authenticationBrowser = mock(AuthenticationBrowser.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        Sca4djIdentityProviderProperties properties = new Sca4djIdentityProviderProperties();
        properties.getOidc().setCompletionUri(URI.create("https://app.example.com/login"));
        when(authenticationBrowser.require(servletRequest)).thenReturn("browser-hash");
        when(oidc.callback("state-1", null, "access_denied", "browser-hash"))
                .thenThrow(new BizException(IdentityProviderErrorCode.OIDC_AUTHORIZATION_REJECTED));
        OidcAuthenticationController controller = new OidcAuthenticationController(
                oidc, authenticationBrowser, properties);

        var response = controller.callback(
                "state-1", null, "access_denied", servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation())
                .hasToString("https://app.example.com/login?oidcFailed=true");
        assertThat(response.getHeaders().getLocation().toString())
                .doesNotContain("state-1", "access_denied", "2317", "2322");
    }

    @Test
    void shouldRejectCallbackBeforeConsumingStateWhenCompletionUriIsMissing() {
        OidcAuthenticationService oidc = mock(OidcAuthenticationService.class);
        OidcAuthenticationController controller = new OidcAuthenticationController(
                oidc, mock(AuthenticationBrowser.class), new Sca4djIdentityProviderProperties());

        assertThatThrownBy(() -> controller.callback(
                "state-1", "authorization-code", null, mock(HttpServletRequest.class)))
                .isInstanceOf(InternalServiceException.class)
                .hasMessage("OIDC completion-uri 未配置");
        verifyNoInteractions(oidc);
    }
}
