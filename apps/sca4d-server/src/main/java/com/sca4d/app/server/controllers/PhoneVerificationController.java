package com.sca4d.app.server.controllers;

import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.authentication.RequireAuthentication;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.PlatformAccountApplicationService;
import com.sca4d.core.api.authentication.RequireSessionToken;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 在 SMS Code 动作认证完成后把当前 Profile 手机号绑定为 PHONE 登录标识。 */
@RestController
@RequestMapping("/me/phone-identifier")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public final class PhoneVerificationController {

    private final PlatformAccountApplicationService accounts;

    /** @param accounts Platform Account 应用服务 */
    public PhoneVerificationController(@NotNull PlatformAccountApplicationService accounts) {
        this.accounts = Objects.requireNonNull(accounts, "Platform Account 应用服务不能为空");
    }

    /** 验证并绑定当前 Profile 手机号。 */
    @PutMapping
    @RequireSessionToken
    @RequireAuthentication(AuthenticationActions.PHONE_VERIFY_CODE)
    @Operation(summary = "验证并绑定当前 Profile 手机号")
    public @NotNull ApiResponse<Void> verify(@CurrentPrincipal @NotNull Long principalId) {
        accounts.verifyProfilePhone(principalId);
        return ApiResponse.noContent();
    }
}
