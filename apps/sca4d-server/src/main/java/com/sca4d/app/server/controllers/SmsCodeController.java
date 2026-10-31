package com.sca4d.app.server.controllers;

import com.sca4d.app.server.authentication.request.SmsCodeChallengeRequest;
import com.sca4d.app.server.authentication.request.SmsCodeProofRequest;
import com.sca4d.app.server.authentication.view.LoginResultView;
import com.sca4d.app.server.authentication.view.SmsCodeChallengeView;
import com.sca4d.app.server.authentication.view.VerificationView;
import com.sca4d.app.server.service.ActionAuthenticationService;
import com.sca4d.app.server.service.PlatformLoginService;
import com.sca4d.auth.sms.SmsCodeAuthenticationMethod;
import com.sca4d.auth.sms.SmsCodeProof;
import com.sca4d.auth.sms.challenge.SmsCodeChallengeService;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.authentication.AuthenticationAttemptId;
import com.sca4d.core.api.authentication.AuthenticationAttempts;
import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.concurrent.RateLimit;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

/** 签发 SMS Challenge，并向登录或敏感动作认证尝试提交验证码。 */
@RestController
@RequestMapping
@Tag(name = "Authentication")
public class SmsCodeController {
    private final ActionAuthenticationService actions;

    private final PlatformLoginService login;

    private final SmsCodeChallengeService challenges;

    private final AuthenticationAttempts authentication;

    /** 创建 SMS Code HTTP 入口。 */
    public SmsCodeController(
            @NotNull SmsCodeChallengeService challenges,
            @NotNull AuthenticationAttempts authentication,
            @NotNull PlatformLoginService login,
            @NotNull ActionAuthenticationService actions) {
        this.challenges = Objects.requireNonNull(challenges, "SMS Challenge 服务不能为空");
        this.authentication = Objects.requireNonNull(authentication, "认证尝试入口不能为空");
        this.login = Objects.requireNonNull(login, "登录服务不能为空");
        this.actions = Objects.requireNonNull(actions, "动作认证服务不能为空");
    }

    /** 为匿名登录签发 SMS Challenge。 */
    @PostMapping("/login/sms-code/challenges")
    @AnonymousAccess
    @Operation(summary = "签发 SMS Code 登录 Challenge")
    public @NotNull ApiResponse<SmsCodeChallengeView> loginChallenge(
            @RequestBody @Valid @NotNull SmsCodeChallengeRequest request) {
        return ApiResponse.ok(SmsCodeChallengeView.from(challenges.issue(request.getPhoneNumber())));
    }

    /** 使用 SMS 验证码开始匿名登录。 */
    @PostMapping("/login/sms-code/proofs")
    @AnonymousAccess
    @RateLimited(dimensions = RateLimit.Dim.IP, permits = 10, windowSeconds = 300)
    @Operation(summary = "使用 SMS Code 开始登录")
    public @NotNull ApiResponse<LoginResultView> login(@RequestBody @Valid @NotNull SmsCodeProofRequest request) {
        LoginResultView result = LoginResultView.from(login.start(submission(request)));
        return ApiResponse.created(result, URI.create("/login/attempts/" + result.getAuthentication().getAttemptId()));
    }

    /** 为指定登录尝试签发 SMS Challenge。 */
    @PostMapping("/login/attempts/{attemptId}/sms-code/challenges")
    @AnonymousAccess
    public @NotNull ApiResponse<SmsCodeChallengeView> loginAttemptChallenge(
            @PathVariable @Size(max = 128) @NotNull String attemptId) {
        return ApiResponse.ok(
                SmsCodeChallengeView.from(
                        challenges.issue(authentication.account(attempt(attemptId)))));
    }

    /** 向指定登录尝试提交 SMS 验证码。 */
    @PostMapping("/login/attempts/{attemptId}/sms-code/proofs")
    @AnonymousAccess
    @RateLimited(dimensions = RateLimit.Dim.IP, permits = 10, windowSeconds = 300)
    public @NotNull ApiResponse<LoginResultView> loginAttempt(
            @PathVariable @Size(max = 128) @NotNull String attemptId,
            @RequestBody @Valid @NotNull SmsCodeProofRequest request) {
        return ApiResponse.ok(LoginResultView.from(login.submit(attempt(attemptId), submission(request))));
    }

    /** 为当前会话的敏感动作认证尝试签发 SMS Challenge。 */
    @PostMapping("/verifications/{verificationId}/sms-code/challenges")
    public @NotNull ApiResponse<SmsCodeChallengeView> verificationChallenge(
            @PathVariable @Size(max = 128) @NotNull String verificationId,
            @CurrentSession @NotNull Session session) {
        return ApiResponse.ok(SmsCodeChallengeView.from(challenges.issue(account(session, attempt(verificationId)))));
    }

    /** 向当前会话的敏感动作认证尝试提交 SMS 验证码。 */
    @PostMapping("/verifications/{verificationId}/sms-code/proofs")
    @RateLimited(dimensions = RateLimit.Dim.IP, permits = 10, windowSeconds = 300)
    public @NotNull ApiResponse<VerificationView> verification(
            @PathVariable @Size(max = 128) @NotNull String verificationId,
            @CurrentSession @NotNull Session session,
            @RequestBody @Valid @NotNull SmsCodeProofRequest request) {
        AuthenticationAttemptId id = attempt(verificationId);
        account(session, id);
        return ApiResponse.ok(VerificationView.from(actions.submit(id, submission(request))));
    }

    private static AuthenticationSubmission<SmsCodeProof> submission(SmsCodeProofRequest request) {
        return new AuthenticationSubmission<>(
                SmsCodeAuthenticationMethod.CODE,
                SmsCodeProof.class,
                new SmsCodeProof(request.challengeId(), request.getCode()));
    }

    private static AuthenticationAttemptId attempt(String value) {
        return new AuthenticationAttemptId(value);
    }

    private Account account(Session session, AuthenticationAttemptId attemptId) {
        Account account = authentication.account(attemptId);
        if (account.accountId() != session.account().accountId()) {
            throw new BusinessException(CoreErrorCode.PERMISSION_DENIED);
        }
        return account;
    }
}
