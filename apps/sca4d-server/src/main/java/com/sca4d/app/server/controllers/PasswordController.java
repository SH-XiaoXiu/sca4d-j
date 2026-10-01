package com.sca4d.app.server.controllers;

import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.authentication.RequireAuthentication;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.platform.request.ReplacePasswordRequest;
import com.sca4d.app.server.platform.view.PasswordView;
import com.sca4d.auth.engine.AuthenticationMethods;
import com.sca4d.auth.password.PasswordAuthenticationMethod;
import com.sca4d.auth.password.credential.PasswordCredential;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.authentication.RequireSessionToken;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentAccount;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 查询和修改当前账号的 Password Credential，不向 HTTP 暴露密码摘要。 */
@RestController
@RequestMapping("/me/password")
@Tag(name = "Current Account")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class PasswordController {

    private final PasswordAuthenticationMethod passwords;

    /** 创建当前账号 Password 入口。 */
    public PasswordController(@NotNull AuthenticationMethods authenticationMethods) {
        this.passwords = Objects.requireNonNull(authenticationMethods, "认证方法目录不能为空")
                .method(PasswordAuthenticationMethod.class);
    }

    /** 查询当前账号的 Password Credential 状态。 */
    @GetMapping
    @Operation(summary = "查询当前账号密码状态")
    public @NotNull ApiResponse<PasswordView> password(
            @CurrentAccount @NotNull Long accountId) {
        PasswordCredential credential = passwords.loadByAccountId(accountId);
        return ApiResponse.ok(PasswordView.from(credential));
    }

    /** 替换 Password Credential，成功后由模块终止当前账号全部 Session。 */
    @PutMapping
    @RequireSessionToken
    @RequireAuthentication(AuthenticationActions.PASSWORD_REPLACE_CODE)
    @Operation(summary = "修改当前账号密码")
    @Audit(operation = "AUTHENTICATION/ACCOUNT/PASSWORD/REPLACE")
    public @NotNull ApiResponse<Void> replace(
            @CurrentAccount @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) @NotNull Long accountId,
            @RequestBody @Valid @NotNull ReplacePasswordRequest request) {
        passwords.replace(accountId, request.getPassword());
        return ApiResponse.noContent();
    }
}
