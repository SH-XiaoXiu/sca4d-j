package com.sca4d.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.audit.AuthenticationAuditOperations;
import com.sca4d.authentication.passkey.PasskeyAssertionStart;
import com.sca4d.authentication.passkey.PasskeyAuthenticationService;
import com.sca4d.authentication.passkey.request.PasskeyProofRequest;
import com.sca4d.authentication.passkey.view.PasskeyAssertionView;
import com.sca4d.authentication.view.AuthenticationView;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.annotation.AnonymousAccess;
import com.sca4d.common.concurrent.ratelimit.RateLimit;
import com.sca4d.common.concurrent.ratelimit.RateLimitType;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.iam.resource.IamResourceTypes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 为一次认证创建 Passkey assertion 并提交 WebAuthn 响应。 */
@RestController
@RequestMapping("/authentications/{authenticationId}")
@AnonymousAccess
@ConditionalOnProperty(prefix = "sca4dj.authentication.passkey", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PasskeyAuthenticationController {

    private final PasskeyAuthenticationService service;
    private final AuthenticationBrowser authenticationBrowser;
    private final ObjectMapper objectMapper;

    /** @return 201 和浏览器调用 {@code navigator.credentials.get()} 的 options */
    @PostMapping("/passkey-assertions")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#authenticationId", capacity = 10, refillTokens = 10, timeWindowSeconds = 300)
    @Audit(module = AuthenticationAuditOperations.LOGIN, action = AuthenticationAuditOperations.PASSKEY_ASSERTION, summary = "#error == null ? 'audit.summary.authentication.login.passkey-assertion.success' : 'audit.summary.authentication.login.passkey-assertion.failed'")
    public @NotNull ApiResponse<PasskeyAssertionView> createAssertion(
            @PathVariable @NotNull String authenticationId,
            @NotNull HttpServletRequest request) {
        PasskeyAssertionStart start = service.createAssertion(
                authenticationId, authenticationBrowser.require(request));
        return ApiResponse.created(PasskeyAssertionView.from(start, objectMapper));
    }

    /** @return WebAuthn assertion 验证后当前认证正在等待的下一项输入 */
    @PostMapping("/passkey-proofs")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#body.assertionId", capacity = 10, refillTokens = 10, timeWindowSeconds = 300)
    @Audit(module = AuthenticationAuditOperations.LOGIN, action = AuthenticationAuditOperations.PASSKEY, targetType = IamResourceTypes.ACCOUNT, targetId = "#result?.data?.accountId", summary = "#error == null ? 'audit.summary.authentication.login.passkey.success' : 'audit.summary.authentication.login.passkey.failed'")
    public @NotNull ApiResponse<AuthenticationView> passkeyProof(
            @PathVariable @NotNull String authenticationId,
            @Valid @RequestBody @NotNull PasskeyProofRequest body,
            @NotNull HttpServletRequest request) {
        AuthenticationProcess process = service.proof(
                authenticationId,
                body.getAssertionId(),
                authenticationBrowser.require(request),
                body.responseJson());
        return ApiResponse.ok(AuthenticationView.from(process));
    }
}
