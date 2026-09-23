package com.sca4d.app.server.controllers;

import com.sca4d.app.server.audit.ApplicationAuditValues;
import com.sca4d.app.server.authentication.AccountAuthenticationSettings;
import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.authentication.AuthenticationMethodSettings;
import com.sca4d.app.server.authentication.RequireAuthentication;
import com.sca4d.app.server.authentication.request.ReplaceAuthenticationSettingsRequest;
import com.sca4d.app.server.authentication.view.AuthenticationSettingsView;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.PlatformAuthenticationPolicyService;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditPayload;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.setting.SettingValue;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentAccount;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.IfMatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Objects;

/** 管理当前账号的动作认证因素数量和认证方法开关。 */
@RestController
@RequestMapping("/me/authentication/settings")
@Tag(name = "Account Authentication")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class AccountAuthenticationSettingsController {

    private final PlatformAuthenticationPolicyService settings;

    /** 创建当前账号认证设置入口。 */
    public AccountAuthenticationSettingsController(
            @NotNull PlatformAuthenticationPolicyService settings) {
        this.settings = Objects.requireNonNull(settings, "账号认证设置服务不能为空");
    }

    /** 查询当前账号显式设置或示例应用默认值。 */
    @GetMapping
    @Operation(summary = "查询当前账号认证设置")
    public @NotNull ApiResponse<AuthenticationSettingsView> settings(
            @CurrentAccount @NotNull Account account) {
        SettingValue<AccountAuthenticationSettings> current = settings.inspect(account.accountId());
        return ApiResponse.ok(AuthenticationSettingsView.from(current));
    }

    /** 单次消费对应动作认证后完整替换当前账号设置。 */
    @PutMapping
    @IfMatch
    @RequireAuthentication(AuthenticationActions.AUTHENTICATION_SETTINGS_REPLACE_CODE)
    @Operation(summary = "替换当前账号认证设置")
    @Audit(operation = "authentication/account/settings/replace", payloadFactory = ApplicationAuditValues.MutationDetails.class)
    public @NotNull ApiResponse<AuthenticationSettingsView> replace(
            @CurrentAccount @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) @NotNull Account account,
            @RequestBody @Valid @AuditPayload @NotNull ReplaceAuthenticationSettingsRequest request) {
        var methods = request.getMethods()
                .stream()
                .map(
                        method -> new AuthenticationMethodSettings(
                                TreeCode.ofSlash(method.getMethodCode()),
                                method.isLoginStartEnabled(),
                                method.isLoginAdditionalEnabled(),
                                method.isSensitiveActionEnabled()))
                .toList();
        SettingValue<AccountAuthenticationSettings> replaced = settings.replace(
                account,
                request.getLoginFactorCount(),
                request.getSensitiveActionFactorCount(),
                Duration.ofSeconds(request.getReusableAuthenticationMaximumAgeSeconds()),
                methods);
        return ApiResponse.ok(AuthenticationSettingsView.from(replaced));
    }
}
