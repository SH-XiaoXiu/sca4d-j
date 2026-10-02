package com.sca4d.app.server.controllers;

import com.sca4d.app.server.authentication.request.PasskeyProofRequest;
import com.sca4d.app.server.authentication.request.PasswordLoginRequest;
import com.sca4d.app.server.authentication.view.LoginResultView;
import com.sca4d.app.server.service.PlatformLoginService;
import com.sca4d.auth.passkey.PasskeyAuthenticationMethod;
import com.sca4d.auth.passkey.PasskeyProof;
import com.sca4d.auth.password.PasswordAuthenticationMethod;
import com.sca4d.auth.password.PasswordProof;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.concurrent.RateLimit;
import com.sca4d.core.spring.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

/** 使用能够识别账号的认证方法开始示例应用登录。 */
@RestController
@RequestMapping("/login/attempts")
@Tag(name = "Authentication")
public class AuthenticationController {

    private final PlatformLoginService login;

    /** 创建登录入口。 */
    public AuthenticationController(@NotNull PlatformLoginService login) {
        this.login = Objects.requireNonNull(login, "登录服务不能为空");
    }

    /** 使用 Sca4d Server 已配置的账号标识和密码开始登录。 */
    @PostMapping
    @AnonymousAccess
    @RateLimited(dimensions = RateLimit.Dim.IP, permits = 10, windowSeconds = 300)
    @Operation(summary = "使用密码开始登录")
    public @NotNull ApiResponse<LoginResultView> password(
            @RequestBody @Valid @NotNull PasswordLoginRequest request) {
        LoginResultView result = LoginResultView.from(
                login.start(
                        new AuthenticationSubmission<>(
                                PasswordAuthenticationMethod.CODE,
                                PasswordProof.class,
                                new PasswordProof(request.getIdentifier(), request.getPassword()))));
        return ApiResponse.created(
                result,
                URI.create("/login/attempts/" + result.getAuthentication().getAttemptId()));
    }

    /** 使用 discoverable Passkey assertion 开始登录。 */
    @PostMapping("/passkey")
    @AnonymousAccess
    @RateLimited(dimensions = RateLimit.Dim.IP, permits = 10, windowSeconds = 300)
    @Operation(summary = "使用 Passkey 开始登录")
    public @NotNull ApiResponse<LoginResultView> passkey(
            @RequestBody @Valid @NotNull PasskeyProofRequest request) {
        LoginResultView result = LoginResultView.from(
                login.start(
                        new AuthenticationSubmission<>(
                                PasskeyAuthenticationMethod.CODE,
                                PasskeyProof.class,
                                new PasskeyProof(request.ceremonyId(), request.getResponseJson()))));
        return ApiResponse.created(
                result,
                URI.create("/login/attempts/" + result.getAuthentication().getAttemptId()));
    }
}
