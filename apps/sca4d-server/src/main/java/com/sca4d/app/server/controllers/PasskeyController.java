package com.sca4d.app.server.controllers;

import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.authentication.RequireAuthentication;
import com.sca4d.app.server.authentication.request.PasskeyRegistrationOptionsRequest;
import com.sca4d.app.server.authentication.request.PasskeyRegistrationRequest;
import com.sca4d.app.server.authentication.view.PasskeyCredentialView;
import com.sca4d.app.server.authentication.view.PasskeyOptionsView;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.PasskeyDeletionService;
import com.sca4d.auth.passkey.ceremony.PasskeyAssertionService;
import com.sca4d.auth.passkey.registration.PasskeyRegistrationService;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentAccount;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** 开始 Passkey assertion，并管理当前账号的多个 Passkey Credential。 */
@RestController
@RequestMapping
@Tag(name = "Passkey")
public class PasskeyController {

    private final PasskeyAssertionService assertions;

    private final PasskeyRegistrationService registrations;

    private final PasskeyDeletionService deletions;

    /** 创建 Passkey HTTP 入口。 */
    public PasskeyController(
            @NotNull PasskeyAssertionService assertions,
            @NotNull PasskeyRegistrationService registrations,
            @NotNull PasskeyDeletionService deletions) {
        this.assertions = Objects.requireNonNull(assertions, "Passkey assertion 服务不能为空");
        this.registrations = Objects.requireNonNull(registrations, "Passkey registration 服务不能为空");
        this.deletions = Objects.requireNonNull(deletions, "Passkey 删除服务不能为空");
    }

    /** 开始可用于登录或动作认证的 discoverable assertion。 */
    @PostMapping("/passkey/assertions/options")
    @AnonymousAccess
    @Operation(summary = "开始 Passkey assertion")
    public @NotNull ApiResponse<PasskeyOptionsView> assertionOptions() {
        return ApiResponse.ok(PasskeyOptionsView.from(assertions.start()));
    }

    /** 查询当前账号的全部有效 Passkey Credential。 */
    @GetMapping("/me/passkeys")
    public @NotNull ApiResponse<List<PasskeyCredentialView>> passkeys(
            @CurrentAccount @NotNull Account account) {
        return ApiResponse.ok(
                registrations.list(account)
                        .stream()
                        .map(PasskeyCredentialView::from)
                        .toList());
    }

    /** 为当前账号开始 discoverable Passkey registration。 */
    @PostMapping("/me/passkeys/registrations/options")
    public @NotNull ApiResponse<PasskeyOptionsView> registrationOptions(
            @CurrentAccount @NotNull Account account,
            @RequestBody @Valid @NotNull PasskeyRegistrationOptionsRequest request) {
        return ApiResponse.ok(
                PasskeyOptionsView.from(
                        registrations.start(account, request.getDisplayName())));
    }

    /** 完成已经通过动作认证的 Passkey registration。 */
    @PostMapping("/me/passkeys")
    @RequireAuthentication(AuthenticationActions.PASSKEY_REGISTER_CODE)
    @CurrentPrincipal(selection = PlatformPrincipalSelection.class)
    @Audit(operation = "AUTHENTICATION/ACCOUNT/PASSKEY/REGISTER")
    public @NotNull ApiResponse<PasskeyCredentialView> register(
            @CurrentAccount @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) @NotNull Account account,
            @RequestBody @Valid @NotNull PasskeyRegistrationRequest request) {
        PasskeyCredentialView created = PasskeyCredentialView.from(
                registrations.finish(
                        account,
                        request.ceremonyId(),
                        request.getResponseJson(),
                        request.getName()));
        return ApiResponse.created(created, URI.create("/me/passkeys/" + created.getId()));
    }

    /** 删除已经通过动作认证的当前账号 Passkey Credential。 */
    @DeleteMapping("/me/passkeys/{credentialId}")
    @RequireAuthentication(AuthenticationActions.PASSKEY_DELETE_CODE)
    @CurrentPrincipal(selection = PlatformPrincipalSelection.class)
    @Audit(operation = "AUTHENTICATION/ACCOUNT/PASSKEY/DELETE")
    public @NotNull ApiResponse<Void> delete(
            @CurrentAccount @NotNull Account account,
            @PathVariable @AuditTarget(resourceType = "AUTHENTICATION/PASSKEY/CREDENTIALS") long credentialId) {
        deletions.delete(account, credentialId);
        return ApiResponse.noContent();
    }
}
