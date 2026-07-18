package com.sca4d.controllers;

import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.annotation.AnonymousAccess;
import com.sca4d.common.concurrent.ratelimit.RateLimit;
import com.sca4d.common.concurrent.ratelimit.RateLimitType;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.config.properties.Sca4djIdentityProviderProperties;
import com.sca4d.identity.oidc.OidcAuthenticationService;
import com.sca4d.identity.oidc.request.OidcAuthorizationCreateRequest;
import com.sca4d.identity.oidc.view.OidcAuthorizationView;
import com.sca4d.identity.oidc.view.OidcAvailableProviderView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** 列出 OIDC 登录入口，并创建授权请求和接收 OpenID Provider callback。 */
@RestController
@RequiredArgsConstructor
public class OidcAuthenticationController {

    private final OidcAuthenticationService oidc;
    private final AuthenticationBrowser authenticationBrowser;
    private final Sca4djIdentityProviderProperties properties;

    /** @return 指定 Account 作用域可选择的 ACTIVE OIDC Provider */
    @AnonymousAccess
    @GetMapping("/auth/oidc/providers")
    public @NotNull ApiResponse<List<OidcAvailableProviderView>> providers(
            @RequestParam @NotNull AccountScopeType scopeType,
            @RequestParam(required = false) @BizId(max = 64) @Nullable String tenantCode) {
        return ApiResponse.ok(oidc.providers(scopeType, tenantCode).stream()
                .map(OidcAvailableProviderView::from)
                .toList());
    }

    /**
     * 为当前认证创建一次 OIDC Authorization Request。
     *
     * @return 包含公开 state、nonce 和 PKCE challenge，但不包含 PKCE verifier 的浏览器跳转地址
     */
    @AnonymousAccess
    @PostMapping("/authentications/{authenticationId}/oidc-authorizations")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#authenticationId", capacity = 10, refillTokens = 10, timeWindowSeconds = 300)
    public @NotNull ApiResponse<OidcAuthorizationView> createAuthorization(
            @PathVariable @NotNull String authenticationId,
            @Valid @RequestBody @NotNull OidcAuthorizationCreateRequest request,
            @NotNull HttpServletRequest servletRequest) {
        return ApiResponse.created(OidcAuthorizationView.from(oidc.authorize(
                authenticationId,
                authenticationBrowser.require(servletRequest),
                request.getProviderCode())));
    }

    /**
     * 单次消费 OIDC state 并在验证 ID Token 后返回登录页。
     *
     * <p>Location 只携带认证 UUID 和当前活动，不包含 Session Token、授权码或上游 Token。
     */
    @AnonymousAccess
    @GetMapping("/auth/oidc/callback")
    public @NotNull ResponseEntity<Void> callback(
            @RequestParam @NotNull String state,
            @RequestParam(required = false) @Nullable String code,
            @RequestParam(required = false) @Nullable String error,
            @NotNull HttpServletRequest servletRequest) {
        URI completionUri = properties.getOidc().getCompletionUri();
        if (completionUri == null) {
            throw new InternalServiceException("OIDC completion-uri 未配置");
        }
        AuthenticationProcess authentication;
        try {
            authentication = oidc.callback(
                    state, code, error, authenticationBrowser.require(servletRequest));
        } catch (BizException exception) {
            URI location = UriComponentsBuilder.fromUri(completionUri)
                    .queryParam("oidcFailed", true)
                    .build()
                    .toUri();
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
        }
        URI location = UriComponentsBuilder.fromUri(completionUri)
                .queryParam("protocol", "OIDC")
                .queryParam("authenticationId", authentication.getAuthenticationId())
                .queryParam("activityId", authentication.getActivityId())
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }
}
