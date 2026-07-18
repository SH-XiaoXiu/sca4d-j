package com.sca4d.controllers;

import com.sca4d.authentication.AuthMethodCatalog;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.AuthenticationPurpose;
import com.sca4d.authentication.audit.AuthenticationAuditOperations;
import com.sca4d.authentication.email.EmailCodeAuthenticationProof;
import com.sca4d.authentication.email.EmailCodeChallengeService;
import com.sca4d.authentication.email.request.EmailCodeProofRequest;
import com.sca4d.authentication.email.view.EmailCodeChallengeView;
import com.sca4d.authentication.password.PasswordAuthenticationProof;
import com.sca4d.authentication.password.request.PasswordProofRequest;
import com.sca4d.authentication.recovery.AccountRecoveryService;
import com.sca4d.authentication.recovery.RecoveryCodeAuthenticationProof;
import com.sca4d.authentication.recovery.request.PasswordReplacementRequest;
import com.sca4d.authentication.recovery.request.RecoveryCodeProofRequest;
import com.sca4d.authentication.request.AuthenticationCreateRequest;
import com.sca4d.authentication.request.SessionCreateRequest;
import com.sca4d.authentication.totp.TotpAuthenticationProof;
import com.sca4d.authentication.totp.request.TotpProofRequest;
import com.sca4d.authentication.view.AuthMethodView;
import com.sca4d.authentication.view.AuthenticationView;
import com.sca4d.authentication.view.LoginSessionView;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.Session;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.auth.annotation.AnonymousAccess;
import com.sca4d.common.concurrent.ratelimit.RateLimit;
import com.sca4d.common.concurrent.ratelimit.RateLimitType;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.iam.resource.IamResourceTypes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 创建认证、提交密码或验证码，并创建或结束登录会话。 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationProcessService authentications;
    private final AccountRecoveryService accountRecoveryService;
    private final EmailCodeChallengeService emailCodeChallenges;
    private final AuthMethodCatalog authMethods;
    private final AuthenticationBrowser authenticationBrowser;
    private final Authentication authentication;

    /** @return 当前应用注册的认证方式及其可用于哪些认证目的 */
    @AnonymousAccess
    @GetMapping("/authentication-methods")
    public @NotNull ApiResponse<List<AuthMethodView>> authenticationMethods() {
        return ApiResponse.ok(authMethods.methods().stream().map(AuthMethodView::from).toList());
    }

    /**
     * 创建绑定当前浏览器和账号范围的短期认证。
     *
     * @param request 认证目的、账号范围和终端信息
     * @param servletRequest 当前 HTTP 请求
     * @param response 写入 HttpOnly 浏览器 Cookie 的响应
     * @return 当前等待提交的认证方式、主体和账号
     */
    @AnonymousAccess
    @PostMapping("/authentications")
    public @NotNull ApiResponse<AuthenticationView> createAuthentication(
            @Valid @RequestBody @NotNull AuthenticationCreateRequest request,
            @NotNull HttpServletRequest servletRequest,
            @NotNull HttpServletResponse response) {
        String browserHash = authenticationBrowser.bind(servletRequest, response);
        AuthenticationProcess process;
        if (request.getPurpose() == AuthenticationPurpose.STEP_UP
                || request.getPurpose() == AuthenticationPurpose.LINKING) {
            // 敏感操作确认和认证方式绑定只能使用当前会话的主体与账号，客户端不能指定其他账号。
            Session currentSession = authentication.currentSession();
            if (currentSession == null) {
                throw new UnauthenticatedException();
            }
            process = authentications.createAuthenticated(
                    request.getPurpose(),
                    currentSession.getSubject(),
                    currentSession.getAccount(),
                    browserHash,
                    request.getDeviceId(),
                    request.getTerminalName());
        } else {
            process = authentications.createAnonymous(
                    request.getPurpose(),
                    request.getScopeType(),
                    request.getTenantCode(),
                    browserHash,
                    request.getDeviceId(),
                    request.getTerminalName());
        }
        return ApiResponse.created(AuthenticationView.from(process));
    }

    /** @return 当前认证正在等待的输入及已经确定的主体和账号 */
    @AnonymousAccess
    @GetMapping("/authentications/{authenticationId}")
    public @NotNull ApiResponse<AuthenticationView> authentication(
            @PathVariable @NotNull String authenticationId,
            @NotNull HttpServletRequest servletRequest) {
        return ApiResponse.ok(AuthenticationView.from(authentications.requireCurrent(
                authenticationId, authenticationBrowser.require(servletRequest))));
    }

    /**
     * 提交登录标识和密码。
     *
     * @param authenticationId 认证 UUID
     * @param request 登录标识和密码
     * @param servletRequest 提供浏览器绑定 Cookie 的请求
     * @return 密码通过后当前等待的下一项输入
     */
    @AnonymousAccess
    @PostMapping("/authentications/{authenticationId}/password-proofs")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#authenticationId", capacity = 10, refillTokens = 10, timeWindowSeconds = 300)
    @Audit(module = AuthenticationAuditOperations.LOGIN, action = AuthenticationAuditOperations.PASSWORD, targetType = IamResourceTypes.ACCOUNT, targetId = "#result?.data?.accountId", summary = "#error == null ? 'audit.summary.authentication.login.password.success' : 'audit.summary.authentication.login.password.failed'", payload = "T(com.sca4d.authentication.password.audit.PasswordLoginAuditPayload)"
            + ".capture(#request.identifier, null, null, "
            + "#result?.data?.subjectId, #error)")
    public @NotNull ApiResponse<AuthenticationView> passwordProof(
            @PathVariable @NotNull String authenticationId,
            @Valid @RequestBody @NotNull PasswordProofRequest request,
            @NotNull HttpServletRequest servletRequest) {
        AuthenticationProcess process = authentications.proof(
                authenticationId,
                authenticationBrowser.require(servletRequest),
                AuthMethodType.PASSWORD,
                PasswordAuthenticationProof.class,
                new PasswordAuthenticationProof(
                        request.getIdentifierType(), request.getIdentifier(), request.toProof()));
        return ApiResponse.ok(AuthenticationView.from(process));
    }

    /**
     * 提交账号标识和一次性恢复码。
     *
     * @param authenticationId 认证 UUID
     * @param request AccountIdentifier 和用户保存的恢复码
     * @param servletRequest 提供浏览器绑定 Cookie 的请求
     * @return 恢复码通过后当前等待的下一项输入
     */
    @AnonymousAccess
    @PostMapping("/authentications/{authenticationId}/recovery-code-proofs")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#authenticationId", capacity = 5, refillTokens = 5, timeWindowSeconds = 300)
    @Audit(module = AuthenticationAuditOperations.RECOVERY, action = AuthenticationAuditOperations.RECOVERY_CODE)
    public @NotNull ApiResponse<AuthenticationView> recoveryCodeProof(
            @PathVariable @NotNull String authenticationId,
            @Valid @RequestBody @NotNull RecoveryCodeProofRequest request,
            @NotNull HttpServletRequest servletRequest) {
        AuthenticationProcess process = authentications.proof(
                authenticationId,
                authenticationBrowser.require(servletRequest),
                AuthMethodType.RECOVERY_CODE,
                RecoveryCodeAuthenticationProof.class,
                new RecoveryCodeAuthenticationProof(
                        request.getIdentifierType(),
                        request.getIdentifier(),
                        request.getRecoveryCode()));
        return ApiResponse.ok(AuthenticationView.from(process));
    }

    /**
     * 使用已经通过恢复码验证的认证替换密码。
     *
     * @param authenticationId 认证 UUID
     * @param request 只在当前请求中使用的新密码
     * @param servletRequest 提供浏览器绑定 Cookie 的请求
     * @return 204；目标 Account 的全部旧登录会话已经结束
     */
    @AnonymousAccess
    @PostMapping("/authentications/{authenticationId}/password-replacements")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "密码已替换")
    @Audit(module = AuthenticationAuditOperations.RECOVERY, action = AuthenticationAuditOperations.PASSWORD_REPLACEMENT)
    public @NotNull ResponseEntity<Void> replacePassword(
            @PathVariable @NotNull String authenticationId,
            @Valid @RequestBody @NotNull PasswordReplacementRequest request,
            @NotNull HttpServletRequest servletRequest) {
        accountRecoveryService.replacePassword(
                authenticationId, authenticationBrowser.require(servletRequest), request.credential());
        return ResponseEntity.noContent().build();
    }

    /**
     * 为当前认证发送邮件验证码。
     *
     * @param authenticationId 认证 UUID
     * @param servletRequest 提供浏览器绑定 Cookie 和响应语言的请求
     * @return 新建 Challenge 的脱敏收件人、有效期和可重发时间
     */
    @AnonymousAccess
    @PostMapping("/authentications/{authenticationId}/email-code-challenges")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#authenticationId", capacity = 10, refillTokens = 10, timeWindowSeconds = 300)
    @Audit(module = AuthenticationAuditOperations.LOGIN, action = AuthenticationAuditOperations.EMAIL_CODE_CHALLENGE)
    public @NotNull ApiResponse<EmailCodeChallengeView> createEmailCodeChallenge(
            @PathVariable @NotNull String authenticationId,
            @NotNull HttpServletRequest servletRequest) {
        AuthenticationProcess process = authentications.requireProof(
                authenticationId, authenticationBrowser.require(servletRequest), AuthMethodType.EMAIL_CODE);
        return ApiResponse.created(EmailCodeChallengeView.from(
                emailCodeChallenges.issue(process, servletRequest.getLocale())));
    }

    /**
     * 提交邮件验证码。
     *
     * @param authenticationId 认证 UUID
     * @param request Challenge 标识和用户收到的验证码
     * @param servletRequest 提供浏览器绑定 Cookie 的请求
     * @return 验证码通过后当前等待的下一项输入
     */
    @AnonymousAccess
    @PostMapping("/authentications/{authenticationId}/email-code-proofs")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#authenticationId", capacity = 10, refillTokens = 10, timeWindowSeconds = 300)
    @Audit(module = AuthenticationAuditOperations.LOGIN, action = AuthenticationAuditOperations.EMAIL_CODE, targetType = IamResourceTypes.ACCOUNT, targetId = "#result?.data?.accountId", summary = "#error == null ? 'audit.summary.authentication.login.email-code.success' : 'audit.summary.authentication.login.email-code.failed'")
    public @NotNull ApiResponse<AuthenticationView> emailCodeProof(
            @PathVariable @NotNull String authenticationId,
            @Valid @RequestBody @NotNull EmailCodeProofRequest request,
            @NotNull HttpServletRequest servletRequest) {
        AuthenticationProcess process = authentications.proof(
                authenticationId,
                authenticationBrowser.require(servletRequest),
                AuthMethodType.EMAIL_CODE,
                EmailCodeAuthenticationProof.class,
                new EmailCodeAuthenticationProof(authenticationId, request.getChallengeId(), request.getCode()));
        return ApiResponse.ok(AuthenticationView.from(process));
    }

    /**
     * 提交身份验证器当前显示的 TOTP 验证码。
     *
     * @param authenticationId 认证 UUID
     * @param request 身份验证器当前显示的六位验证码
     * @param servletRequest 提供浏览器绑定 Cookie 的请求
     * @return 验证码通过后当前等待的下一项输入
     */
    @AnonymousAccess
    @PostMapping("/authentications/{authenticationId}/totp-proofs")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#authenticationId", capacity = 10, refillTokens = 10, timeWindowSeconds = 300)
    @Audit(module = AuthenticationAuditOperations.LOGIN, action = AuthenticationAuditOperations.TOTP, targetType = IamResourceTypes.ACCOUNT, targetId = "#result?.data?.accountId", summary = "#error == null ? 'audit.summary.authentication.login.totp.success' : 'audit.summary.authentication.login.totp.failed'")
    public @NotNull ApiResponse<AuthenticationView> totpProof(
            @PathVariable @NotNull String authenticationId,
            @Valid @RequestBody @NotNull TotpProofRequest request,
            @NotNull HttpServletRequest servletRequest) {
        AuthenticationProcess process = authentications.proof(
                authenticationId,
                authenticationBrowser.require(servletRequest),
                AuthMethodType.TOTP,
                TotpAuthenticationProof.class,
                new TotpAuthenticationProof(request.getCode()));
        return ApiResponse.ok(AuthenticationView.from(process));
    }

    /**
     * 使用已经完成的认证创建登录会话。
     *
     * @param request 认证 UUID
     * @param servletRequest 提供浏览器绑定 Cookie 的请求
     * @return 新建 Session 的安全展示结果
     */
    @AnonymousAccess
    @PostMapping("/sessions")
    @Audit(module = AuthenticationAuditOperations.LOGIN, action = AuthenticationAuditOperations.SESSION, targetType = IamResourceTypes.ACCOUNT, targetId = "#result?.data?.accountId", summary = "#error == null ? 'audit.summary.authentication.login.session.success' : 'audit.summary.authentication.login.session.failed'")
    public @NotNull ApiResponse<LoginSessionView> createSession(
            @Valid @RequestBody @NotNull SessionCreateRequest request,
            @NotNull HttpServletRequest servletRequest) {
        Session session = authentications.session(
                request.getAuthenticationId(), authenticationBrowser.require(servletRequest));
        CurrentRequest currentRequest = CurrentRequestHolder.current();
        currentRequest.setSession(session);
        currentRequest.setSubject(session.getSubject());
        currentRequest.setAccount(session.getAccount());
        return ApiResponse.created(LoginSessionView.from(session));
    }

    /**
     * 结束当前已认证会话。
     *
     * @return 没有响应数据的成功结果
     */
    @DeleteMapping("/sessions/current")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "登录会话已结束")
    @Audit(module = AuthenticationAuditOperations.SESSIONS, action = AuthenticationAuditOperations.LOGOUT)
    public @NotNull ResponseEntity<Void> deleteSession() {
        authentication.logout();
        return ResponseEntity.noContent().build();
    }
}
