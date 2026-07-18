package com.sca4d.controllers;

import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationHeaders;
import com.sca4d.authentication.audit.AuthenticationAuditOperations;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthAccount;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.IfMatchVersion;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderResourceTypes;
import com.sca4d.identity.external.ExternalIdentityAccountService;
import com.sca4d.identity.external.view.AvailableIdentityProviderView;
import com.sca4d.identity.external.view.ExternalIdentityView;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 当前 Subject 的 ExternalIdentity 查询和安全解除关联资源。 */
@RestController
@RequestMapping("/me/accounts/{accountId}/external-identities")
@RequiredArgsConstructor
public class ExternalIdentityController {

    private final ExternalIdentityAccountService service;
    private final AuthenticationBrowser authenticationBrowser;

    /** @return 当前 Subject 名下指定 Account 的 ExternalIdentity */
    @GetMapping
    public @NotNull ApiResponse<List<ExternalIdentityView>> identities(
            @PathVariable @NotNull Long accountId,
            @AuthSubject @NotNull Subject subject) {
        return ApiResponse.ok(service.identities(subject.getSubjectId(), accountId).stream()
                .map(ExternalIdentityView::from)
                .toList());
    }

    /** @return 当前 Subject 名下指定 Account 可以关联的 ACTIVE IdentityProvider */
    @GetMapping("/providers")
    public @NotNull ApiResponse<List<AvailableIdentityProviderView>> providers(
            @PathVariable @NotNull Long accountId,
            @AuthSubject @NotNull Subject subject) {
        return ApiResponse.ok(service.providers(subject.getSubjectId(), accountId).stream()
                .map(AvailableIdentityProviderView::from)
                .toList());
    }

    /** 使用 STEP_UP 和强版本条件解除一个 ExternalIdentity 关联。 */
    @DeleteMapping("/{identityId}")
    @Audit(module = AuthenticationAuditOperations.ACCOUNT_SECURITY, action = AuthenticationAuditOperations.EXTERNAL_IDENTITY_REMOVAL, targetType = IdentityProviderResourceTypes.EXTERNAL_IDENTITY, targetId = "#identityId", payload = "T(java.util.Map).of('accountId', #accountId)")
    public @NotNull ResponseEntity<Void> delete(
            @PathVariable @NotNull Long accountId,
            @PathVariable @NotNull Long identityId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthSubject @NotNull Subject subject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        service.delete(
                subject,
                authenticatedAccount,
                accountId,
                identityId,
                IfMatchVersion.require(
                        ifMatch,
                        IdentityProviderErrorCode.EXTERNAL_IDENTITY_IF_MATCH_REQUIRED,
                        IdentityProviderErrorCode.EXTERNAL_IDENTITY_CHANGED),
                authenticationId,
                authenticationBrowser.require(request));
        return ResponseEntity.noContent().build();
    }
}
