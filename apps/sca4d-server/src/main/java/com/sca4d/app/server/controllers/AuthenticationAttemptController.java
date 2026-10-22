package com.sca4d.app.server.controllers;

import com.sca4d.app.server.authentication.request.PasskeyProofRequest;
import com.sca4d.app.server.authentication.request.PasswordProofRequest;
import com.sca4d.app.server.authentication.request.StepUpRequest;
import com.sca4d.app.server.authentication.request.TotpProofRequest;
import com.sca4d.app.server.authentication.view.LoginResultView;
import com.sca4d.app.server.authentication.view.VerificationView;
import com.sca4d.app.server.service.ActionAuthenticationService;
import com.sca4d.app.server.service.PlatformLoginService;
import com.sca4d.auth.passkey.PasskeyAuthenticationMethod;
import com.sca4d.auth.passkey.PasskeyProof;
import com.sca4d.auth.password.PasswordAuthenticationMethod;
import com.sca4d.auth.password.PasswordProof;
import com.sca4d.auth.totp.TotpAuthenticationMethod;
import com.sca4d.auth.totp.TotpProof;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationAttemptId;
import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.concurrent.RateLimit;
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

/** 向跨请求登录或登录后动作认证尝试提交认证材料。 */
@RestController
@RequestMapping
@Tag(name = "Authentication")
public class AuthenticationAttemptController {

    private final ActionAuthenticationService actions;

    private final PlatformLoginService login;

    /** 创建认证尝试入口。 */
    public AuthenticationAttemptController(
            @NotNull PlatformLoginService login,
            @NotNull ActionAuthenticationService actions) {
        this.login = Objects.requireNonNull(login, "登录服务不能为空");
        this.actions = Objects.requireNonNull(actions, "动作认证服务不能为空");
    }

    /** 向登录尝试提交 Password。 */
    @PostMapping("/login/attempts/{attemptId}/password/proofs")
    @AnonymousAccess
    @RateLimited(dimensions = RateLimit.Dim.IP, permits = 10, windowSeconds = 300)
    public @NotNull ApiResponse<LoginResultView> loginPassword(
            @PathVariable @Size(max = 128) @NotNull String attemptId,
            @RequestBody @Valid @NotNull PasswordProofRequest request) {
        return ApiResponse.ok(
                LoginResultView.from(
                        login.submit(
                                new AuthenticationAttemptId(attemptId),
                                new AuthenticationSubmission<>(
                                        PasswordAuthenticationMethod.CODE,
                                        PasswordProof.class,
                                        new PasswordProof(null, request.getPassword())))));
    }

    /** 向登录尝试提交 TOTP。 */
    @PostMapping("/login/attempts/{attemptId}/totp/proofs")
    @AnonymousAccess
    @RateLimited(dimensions = RateLimit.Dim.IP, permits = 10, windowSeconds = 300)
    public @NotNull ApiResponse<LoginResultView> loginTotp(
            @PathVariable @Size(max = 128) @NotNull String attemptId,
            @RequestBody @Valid @NotNull TotpProofRequest request) {
        return ApiResponse.ok(
                LoginResultView.from(
                        login.submit(
                                new AuthenticationAttemptId(attemptId),
                                new AuthenticationSubmission<>(
                                        TotpAuthenticationMethod.CODE,
                                        TotpProof.class,
                                        new TotpProof(request.getCode())))));
    }

    /** 向登录尝试提交 Passkey。 */
    @PostMapping("/login/attempts/{attemptId}/passkey/proofs")
    @AnonymousAccess
    @RateLimited(dimensions = RateLimit.Dim.IP, permits = 10, windowSeconds = 300)
    public @NotNull ApiResponse<LoginResultView> loginPasskey(
            @PathVariable @Size(max = 128) @NotNull String attemptId,
            @RequestBody @Valid @NotNull PasskeyProofRequest request) {
        return ApiResponse.ok(
                LoginResultView.from(
                        login.submit(
                                new AuthenticationAttemptId(attemptId),
                                new AuthenticationSubmission<>(
                                        PasskeyAuthenticationMethod.CODE,
                                        PasskeyProof.class,
                                        new PasskeyProof(request.ceremonyId(), request.getResponseJson())))));
    }

    /** 为当前 Session 开始指定动作认证。 */
    @PostMapping("/verifications")
    @Operation(summary = "开始动作认证")
    public @NotNull ApiResponse<VerificationView> start(
            @CurrentSession @NotNull Session session,
            @RequestBody @Valid @NotNull StepUpRequest request) {
        VerificationView verification = VerificationView.from(
                actions.start(
                        session,
                        new AuthenticationActionCode(request.getActionCode())));
        return ApiResponse.created(
                verification,
                URI.create("/verifications/" + verification.getVerificationId()));
    }

    /** 向动作认证尝试提交 Password。 */
    @PostMapping("/verifications/{verificationId}/password/proofs")
    @RateLimited(dimensions = RateLimit.Dim.IP, permits = 10, windowSeconds = 300)
    public @NotNull ApiResponse<VerificationView> password(
            @PathVariable @Size(max = 128) @NotNull String verificationId,
            @RequestBody @Valid @NotNull PasswordProofRequest request) {
        return ApiResponse.ok(
                VerificationView.from(
                        actions.submit(
                                new AuthenticationAttemptId(verificationId),
                                new AuthenticationSubmission<>(
                                        PasswordAuthenticationMethod.CODE,
                                        PasswordProof.class,
                                        new PasswordProof(null, request.getPassword())))));
    }

    /** 向动作认证尝试提交 TOTP。 */
    @PostMapping("/verifications/{verificationId}/totp/proofs")
    @RateLimited(dimensions = RateLimit.Dim.IP, permits = 10, windowSeconds = 300)
    public @NotNull ApiResponse<VerificationView> totp(
            @PathVariable @Size(max = 128) @NotNull String verificationId,
            @RequestBody @Valid @NotNull TotpProofRequest request) {
        return ApiResponse.ok(
                VerificationView.from(
                        actions.submit(
                                new AuthenticationAttemptId(verificationId),
                                new AuthenticationSubmission<>(
                                        TotpAuthenticationMethod.CODE,
                                        TotpProof.class,
                                        new TotpProof(request.getCode())))));
    }

    /** 向动作认证尝试提交 Passkey。 */
    @PostMapping("/verifications/{verificationId}/passkey/proofs")
    @RateLimited(dimensions = RateLimit.Dim.IP, permits = 10, windowSeconds = 300)
    public @NotNull ApiResponse<VerificationView> passkey(
            @PathVariable @Size(max = 128) @NotNull String verificationId,
            @RequestBody @Valid @NotNull PasskeyProofRequest request) {
        return ApiResponse.ok(
                VerificationView.from(
                        actions.submit(
                                new AuthenticationAttemptId(verificationId),
                                new AuthenticationSubmission<>(
                                        PasskeyAuthenticationMethod.CODE,
                                        PasskeyProof.class,
                                        new PasskeyProof(request.ceremonyId(), request.getResponseJson())))));
    }
}
