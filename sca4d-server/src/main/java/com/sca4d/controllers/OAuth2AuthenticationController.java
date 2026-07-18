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
import com.sca4d.identity.oauth2.OAuth2AuthenticationService;
import com.sca4d.identity.oauth2.request.OAuth2AuthorizationCreateRequest;
import com.sca4d.identity.oauth2.view.OAuth2AuthorizationView;
import com.sca4d.identity.oauth2.view.OAuth2AvailableProviderView;
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

/** 列出 OAuth2 登录入口，并创建授权请求和接收 OAuth2 Provider callback。 */
@RestController
@RequiredArgsConstructor
public class OAuth2AuthenticationController {

    private final OAuth2AuthenticationService oauth2;
    private final AuthenticationBrowser authenticationBrowser;
    private final Sca4djIdentityProviderProperties properties;

    /** @return 指定 Account 作用域可选择的 ACTIVE OAuth2 Provider */
    @AnonymousAccess
    @GetMapping("/auth/oauth2/providers")
    public @NotNull ApiResponse<List<OAuth2AvailableProviderView>> providers(
            @RequestParam @NotNull AccountScopeType scopeType,
            @RequestParam(required = false) @BizId(max = 64) @Nullable String tenantCode) {
        return ApiResponse.ok(oauth2.providers(scopeType, tenantCode).stream()
                .map(OAuth2AvailableProviderView::from)
                .toList());
    }

    /** @return 包含 state 和 PKCE challenge、但不包含 verifier 的浏览器跳转地址 */
    @AnonymousAccess
    @PostMapping("/authentications/{authenticationId}/oauth2-authorizations")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#authenticationId", capacity = 10, refillTokens = 10, timeWindowSeconds = 300)
    public @NotNull ApiResponse<OAuth2AuthorizationView> createAuthorization(
            @PathVariable @NotNull String authenticationId,
            @Valid @RequestBody @NotNull OAuth2AuthorizationCreateRequest request,
            @NotNull HttpServletRequest servletRequest) {
        return ApiResponse.created(OAuth2AuthorizationView.from(oauth2.authorize(
                authenticationId,
                authenticationBrowser.require(servletRequest),
                request.getProviderCode())));
    }

    /** 单次消费 OAuth2 state，完成 Profile 映射后返回登录页。 */
    @AnonymousAccess
    @GetMapping("/auth/oauth2/callback")
    public @NotNull ResponseEntity<Void> callback(
            @RequestParam @NotNull String state,
            @RequestParam(required = false) @Nullable String code,
            @RequestParam(required = false) @Nullable String error,
            @NotNull HttpServletRequest servletRequest) {
        URI completionUri = properties.getOauth2().getCompletionUri();
        if (completionUri == null) {
            throw new InternalServiceException("OAuth2 completion-uri 未配置");
        }
        AuthenticationProcess authentication;
        try {
            authentication = oauth2.callback(
                    state, code, error, authenticationBrowser.require(servletRequest));
        } catch (BizException exception) {
            URI location = UriComponentsBuilder.fromUri(completionUri)
                    .queryParam("oauth2Failed", true)
                    .build()
                    .toUri();
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
        }
        URI location = UriComponentsBuilder.fromUri(completionUri)
                .queryParam("protocol", "OAUTH2")
                .queryParam("authenticationId", authentication.getAuthenticationId())
                .queryParam("activityId", authentication.getActivityId())
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }
}
