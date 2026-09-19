package com.sca4d.app.server.controllers;

import com.sca4d.app.server.authentication.request.EmailCodeChallengeRequest;
import com.sca4d.app.server.authentication.request.EmailCodeProofRequest;
import com.sca4d.app.server.authentication.view.EmailCodeChallengeView;
import com.sca4d.app.server.authentication.view.LoginResultView;
import com.sca4d.app.server.authentication.view.VerificationView;
import com.sca4d.app.server.service.ActionAuthenticationService;
import com.sca4d.app.server.service.PlatformLoginService;
import com.sca4d.auth.email.EmailCodeAuthenticationMethod;
import com.sca4d.auth.email.EmailCodeProof;
import com.sca4d.auth.email.challenge.EmailCodeChallengeService;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.authentication.AuthenticationAttemptId;
import com.sca4d.core.api.authentication.AuthenticationAttempts;
import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.concurrent.RateLimitDimension;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
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

/** 签发 Email Code Challenge，并向登录或动作认证尝试提交验证码。 */
@RestController
@RequestMapping
@Tag(name = "Authentication")
public class EmailCodeController {

    private final @NotNull EmailCodeChallengeService challenges;

    private final @NotNull AuthenticationAttempts authentication;

    private final @NotNull PlatformLoginService login;

    private final @NotNull ActionAuthenticationService actions;

    /**
     * @param challenges Email Code Challenge 签发服务
     * @param authentication 认证尝试账号读取入口
     * @param login Sca4d Server 登录服务
     * @param actions 登录后动作认证服务
     */
    public EmailCodeController(
            @NotNull EmailCodeChallengeService challenges,
            @NotNull AuthenticationAttempts authentication,
            @NotNull PlatformLoginService login,
            @NotNull ActionAuthenticationService actions) {
        this.challenges = Objects.requireNonNull(challenges, "Email Code Challenge 服务不能为空");
        this.authentication = Objects.requireNonNull(authentication, "认证尝试入口不能为空");
        this.login = Objects.requireNonNull(login, "登录服务不能为空");
        this.actions = Objects.requireNonNull(actions, "动作认证服务不能为空");
    }

    /** 使用邮箱签发匿名一致的登录 Challenge。 */
    @PostMapping("/login/email-code/challenges")
    @AnonymousAccess
    @Operation(summary = "签发 Email Code 登录 Challenge")
    public @NotNull ApiResponse<EmailCodeChallengeView> loginChallenge(
            @RequestBody @Valid @NotNull EmailCodeChallengeRequest request) {
        return ApiResponse.ok(
                EmailCodeChallengeView.from(
                        challenges.issue(request.getEmail())));
    }

    /** 使用已消费的 Email Code Challenge 开始登录。 */
    @PostMapping("/login/email-code/proofs")
    @AnonymousAccess
    @RateLimited(dimensions = RateLimitDimension.IP, permits = 10, windowSeconds = 300)
    @Operation(summary = "使用 Email Code 开始登录")
    public @NotNull ApiResponse<LoginResultView> login(
            @RequestBody @Valid @NotNull EmailCodeProofRequest request) {
        LoginResultView result = LoginResultView.from(login.start(submission(request)));
        return ApiResponse.created(
                result,
                URI.create("/login/attempts/" + result.getAuthentication().getAttemptId()));
    }

    /** 为已经确定 Account 的登录尝试签发后续 Email Code Challenge。 */
    @PostMapping("/login/attempts/{attemptId}/email-code/challenges")
    @AnonymousAccess
    @Operation(summary = "为登录尝试签发 Email Code Challenge")
    public @NotNull ApiResponse<EmailCodeChallengeView> loginAttemptChallenge(
            @PathVariable @Size(max = 128) @NotNull String attemptId) {
        Account account = authentication.account(attempt(attemptId));
        return ApiResponse.ok(EmailCodeChallengeView.from(challenges.issue(account)));
    }

    /** 向已经确定 Account 的登录尝试提交 Email Code。 */
    @PostMapping("/login/attempts/{attemptId}/email-code/proofs")
    @AnonymousAccess
    @RateLimited(dimensions = RateLimitDimension.IP, permits = 10, windowSeconds = 300)
    @Operation(summary = "向登录尝试提交 Email Code")
    public @NotNull ApiResponse<LoginResultView> loginAttempt(
            @PathVariable @Size(max = 128) @NotNull String attemptId,
            @RequestBody @Valid @NotNull EmailCodeProofRequest request) {
        return ApiResponse.ok(
                LoginResultView.from(
                        login.submit(attempt(attemptId), submission(request))));
    }

    /** 为当前 Session 所属动作认证签发 Email Code Challenge。 */
    @PostMapping("/verifications/{verificationId}/email-code/challenges")
    @Operation(summary = "为动作认证签发 Email Code Challenge")
    public @NotNull ApiResponse<EmailCodeChallengeView> verificationChallenge(
            @PathVariable @Size(max = 128) @NotNull String verificationId,
            @CurrentSession @NotNull Session session) {
        Account account = account(session, attempt(verificationId));
        return ApiResponse.ok(EmailCodeChallengeView.from(challenges.issue(account)));
    }

    /** 向当前 Session 所属动作认证提交 Email Code。 */
    @PostMapping("/verifications/{verificationId}/email-code/proofs")
    @RateLimited(dimensions = RateLimitDimension.IP, permits = 10, windowSeconds = 300)
    @Operation(summary = "向动作认证提交 Email Code")
    public @NotNull ApiResponse<VerificationView> verification(
            @PathVariable @Size(max = 128) @NotNull String verificationId,
            @CurrentSession @NotNull Session session,
            @RequestBody @Valid @NotNull EmailCodeProofRequest request) {
        AuthenticationAttemptId attemptId = attempt(verificationId);
        account(session, attemptId);
        return ApiResponse.ok(
                VerificationView.from(
                        actions.submit(attemptId, submission(request))));
    }

    /** 把 HTTP 请求转换成 Email Code 认证方法提交对象。 */
    private static @NotNull AuthenticationSubmission<EmailCodeProof> submission(
            @NotNull EmailCodeProofRequest request) {
        EmailCodeProofRequest checked = Objects.requireNonNull(request, "Email Code Proof 请求不能为空");
        return new AuthenticationSubmission<>(
                EmailCodeAuthenticationMethod.CODE,
                EmailCodeProof.class,
                new EmailCodeProof(checked.challengeId(), checked.getCode()));
    }

    /** 把 HTTP 路径文本转换为认证尝试标识。 */
    private static @NotNull AuthenticationAttemptId attempt(@NotNull String value) {
        return new AuthenticationAttemptId(Objects.requireNonNull(value, "认证尝试标识不能为空"));
    }

    /** 要求动作认证尝试属于当前 Session Account，避免向其他账号发送验证码或消费 Challenge。 */
    private @NotNull Account account(
            @NotNull Session session,
            @NotNull AuthenticationAttemptId attemptId) {
        Account account = authentication.account(attemptId);
        if (account.accountId() != Objects.requireNonNull(session, "Session 不能为空").account().accountId()) {
            throw new BusinessException(CoreErrorCode.PERMISSION_DENIED);
        }
        return account;
    }
}
