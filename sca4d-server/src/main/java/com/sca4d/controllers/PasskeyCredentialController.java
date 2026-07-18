package com.sca4d.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationHeaders;
import com.sca4d.authentication.audit.AuthenticationAuditOperations;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthAccount;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.common.concurrent.ratelimit.RateLimit;
import com.sca4d.common.concurrent.ratelimit.RateLimitType;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.IfMatchVersion;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.passkey.PasskeyCredentialChange;
import com.sca4d.iam.passkey.PasskeyRegistrationService;
import com.sca4d.iam.passkey.PasskeyRegistrationStart;
import com.sca4d.iam.passkey.request.PasskeyCredentialCreateRequest;
import com.sca4d.iam.passkey.request.PasskeyRegistrationCreateRequest;
import com.sca4d.iam.passkey.view.PasskeyCredentialChangeView;
import com.sca4d.iam.passkey.view.PasskeyRegistrationView;
import com.sca4d.iam.resource.IamResourceTypes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前 Subject 的 Passkey registration 和 Credential 自助资源。 */
@RestController
@RequestMapping("/me/accounts/{accountId}/passkey")
@AuthPrincipal
@ConditionalOnProperty(prefix = "sca4dj.authentication.passkey", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PasskeyCredentialController {

    private final PasskeyRegistrationService service;
    private final AuthenticationBrowser authenticationBrowser;
    private final ObjectMapper objectMapper;

    /** @return 201 和浏览器调用 {@code navigator.credentials.create()} 的 options */
    @PostMapping("/registrations")
    @Audit(module = AuthenticationAuditOperations.ACCOUNT_SECURITY, action = AuthenticationAuditOperations.PASSKEY_REGISTRATION, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId")
    public @NotNull ApiResponse<PasskeyRegistrationView> createRegistration(
            @PathVariable @NotNull Long accountId,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @Valid @RequestBody @NotNull PasskeyRegistrationCreateRequest body,
            @AuthSubject @NotNull Subject subject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        PasskeyRegistrationStart start = service.create(
                subject,
                authenticatedAccount,
                accountId,
                authenticationId,
                authenticationBrowser.require(request),
                body.getDisplayName());
        return ApiResponse.created(PasskeyRegistrationView.from(start, objectMapper));
    }

    /** @return 201、新 Credential 主键和强 ETag */
    @PostMapping("/credentials")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#body.registrationId", capacity = 10, refillTokens = 10, timeWindowSeconds = 300)
    @Audit(module = AuthenticationAuditOperations.ACCOUNT_SECURITY, action = AuthenticationAuditOperations.PASSKEY_ACTIVATION, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId")
    public @NotNull ApiResponse<PasskeyCredentialChangeView> activate(
            @PathVariable @NotNull Long accountId,
            @Valid @RequestBody @NotNull PasskeyCredentialCreateRequest body,
            @AuthSubject @NotNull Subject subject,
            @NotNull HttpServletRequest request) {
        PasskeyCredentialChange change = service.activate(
                subject.getSubjectId(),
                accountId,
                body.getRegistrationId(),
                body.responseJson(),
                authenticationBrowser.require(request));
        return ApiResponse.created(PasskeyCredentialChangeView.from(change), change.getVersion());
    }

    /** @return 204；Passkey 已删除且目标 Account 全部旧 Session 已结束 */
    @DeleteMapping("/credentials/{credentialId}")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Passkey Credential 已删除")
    @Audit(module = AuthenticationAuditOperations.ACCOUNT_SECURITY, action = AuthenticationAuditOperations.PASSKEY_REMOVAL, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId", payload = "T(java.util.Map).of('credentialId', #credentialId)")
    public @NotNull ResponseEntity<Void> delete(
            @PathVariable @NotNull Long accountId,
            @PathVariable @NotNull Long credentialId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthSubject @NotNull Subject subject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        service.delete(
                subject,
                authenticatedAccount,
                accountId,
                credentialId,
                IfMatchVersion.require(
                        ifMatch,
                        IamErrorCode.PASSKEY_CREDENTIAL_IF_MATCH_REQUIRED,
                        IamErrorCode.PASSKEY_CREDENTIAL_CHANGED),
                authenticationId,
                authenticationBrowser.require(request));
        return ResponseEntity.noContent().build();
    }
}
