package com.sca4d.controllers;

import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.view.AuthenticationView;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.annotation.AnonymousAccess;
import com.sca4d.common.concurrent.ratelimit.RateLimit;
import com.sca4d.common.concurrent.ratelimit.RateLimitType;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.identity.ldap.LdapAuthenticationService;
import com.sca4d.identity.ldap.request.LdapProofRequest;
import com.sca4d.identity.ldap.view.LdapAvailableProviderView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 列出 LDAP 登录入口，并提交目录账号和 LDAP 密码。 */
@RestController
@RequiredArgsConstructor
public class LdapAuthenticationController {

    private final LdapAuthenticationService ldap;
    private final AuthenticationBrowser authenticationBrowser;

    /** @return 指定 Account 作用域可选择的 ACTIVE LDAP Provider */
    @AnonymousAccess
    @GetMapping("/auth/ldap/providers")
    public @NotNull ApiResponse<List<LdapAvailableProviderView>> providers(
            @RequestParam @NotNull AccountScopeType scopeType,
            @RequestParam(required = false) @BizId(max = 64) @Nullable String tenantCode) {
        return ApiResponse.ok(ldap.providers(scopeType, tenantCode).stream()
                .map(LdapAvailableProviderView::from)
                .toList());
    }

    /** 使用 LDAP 搜索和用户 bind 验证目录账号与密码。 */
    @AnonymousAccess
    @PostMapping("/authentications/{authenticationId}/ldap-proofs")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#authenticationId", capacity = 5, refillTokens = 5, timeWindowSeconds = 300)
    public @NotNull ApiResponse<AuthenticationView> proof(
            @PathVariable @NotNull String authenticationId,
            @Valid @RequestBody @NotNull LdapProofRequest request,
            @NotNull HttpServletRequest servletRequest) {
        AuthenticationProcess process = ldap.authenticate(
                authenticationId,
                authenticationBrowser.require(servletRequest),
                request.getProviderCode(),
                request.getUsername(),
                request.getPassword());
        return ApiResponse.ok(AuthenticationView.from(process));
    }
}
