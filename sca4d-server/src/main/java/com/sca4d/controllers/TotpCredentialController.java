package com.sca4d.controllers;

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
import com.sca4d.iam.resource.IamResourceTypes;
import com.sca4d.iam.totp.TotpEnrollmentService;
import com.sca4d.iam.totp.request.TotpCredentialCreateRequest;
import com.sca4d.iam.totp.request.TotpCredentialReplaceRequest;
import com.sca4d.iam.totp.view.TotpCredentialChangeView;
import com.sca4d.iam.totp.view.TotpEnrollmentView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前 Subject 的 TOTP enrollment 和 Credential 自助资源。 */
@RestController
@RequestMapping("/me/accounts/{accountId}/totp")
@AuthPrincipal
@RequiredArgsConstructor
public class TotpCredentialController {

    private final TotpEnrollmentService service;
    private final AuthenticationBrowser authenticationBrowser;

    /**
     * 创建浏览器绑定的短期 TOTP enrollment。
     *
     * @return 201 和 enrollment 标识；密钥只通过二维码资源展示
     */
    @PostMapping("/enrollments")
    @Audit(module = AuthenticationAuditOperations.ACCOUNT_SECURITY, action = AuthenticationAuditOperations.TOTP_ENROLLMENT, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId")
    public @NotNull ApiResponse<TotpEnrollmentView> createEnrollment(
            @PathVariable @NotNull Long accountId,
            @RequestHeader(AuthenticationHeaders.AUTHENTICATION_ID) @BizId(max = 128) @NotNull String authenticationId,
            @AuthSubject @NotNull Subject subject,
            @AuthAccount @NotNull Account authenticatedAccount,
            @NotNull HttpServletRequest request) {
        return ApiResponse.created(TotpEnrollmentView.from(service.create(
                subject,
                authenticatedAccount,
                accountId,
                authenticationId,
                authenticationBrowser.require(request))));
    }

    /**
     * 读取 enrollment 的 otpauth PNG 二维码。
     *
     * @return 仅当前 Subject、Account 和浏览器可读取且禁止缓存的 PNG
     */
    @GetMapping(value = "/enrollments/{enrollmentId}/qr-code", produces = MediaType.IMAGE_PNG_VALUE)
    public @NotNull ResponseEntity<byte[]> qrCode(
            @PathVariable @NotNull Long accountId,
            @PathVariable @BizId(max = 64) @NotNull String enrollmentId,
            @AuthSubject @NotNull Subject subject,
            @NotNull HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(service.qrCode(
                        subject.getSubjectId(),
                        accountId,
                        enrollmentId,
                        authenticationBrowser.require(request)));
    }

    /**
     * 使用 enrollment 首次验证码创建 ACTIVE TOTP Credential。
     *
     * @return 201 和新 Credential 主键、初始版本
     */
    @PostMapping("/credentials")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#body.enrollmentId", capacity = 10, refillTokens = 10, timeWindowSeconds = 300)
    @Audit(module = AuthenticationAuditOperations.ACCOUNT_SECURITY, action = AuthenticationAuditOperations.TOTP_ACTIVATION, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId")
    public @NotNull ApiResponse<TotpCredentialChangeView> activate(
            @PathVariable @NotNull Long accountId,
            @Valid @RequestBody @NotNull TotpCredentialCreateRequest body,
            @AuthSubject @NotNull Subject subject,
            @NotNull HttpServletRequest request) {
        var change = service.activate(
                subject.getSubjectId(),
                accountId,
                body.getEnrollmentId(),
                body.getCode(),
                authenticationBrowser.require(request));
        return ApiResponse.created(TotpCredentialChangeView.from(change), change.getVersion());
    }

    /**
     * 使用新的 enrollment 替换指定 TOTP Credential。
     *
     * @return 替换后的 Credential 主键和版本
     */
    @PutMapping("/credentials/{credentialId}")
    @RateLimit(types = {RateLimitType.IP, RateLimitType.CUSTOM}, key = "#body.enrollmentId", capacity = 10, refillTokens = 10, timeWindowSeconds = 300)
    @Audit(module = AuthenticationAuditOperations.ACCOUNT_SECURITY, action = AuthenticationAuditOperations.TOTP_REPLACEMENT, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId", payload = "T(java.util.Map).of('credentialId', #credentialId)")
    public @NotNull ApiResponse<TotpCredentialChangeView> replace(
            @PathVariable @NotNull Long accountId,
            @PathVariable @NotNull Long credentialId,
            @Valid @RequestBody @NotNull TotpCredentialReplaceRequest body,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @AuthSubject @NotNull Subject subject,
            @NotNull HttpServletRequest request) {
        var change = service.replace(
                subject.getSubjectId(),
                accountId,
                credentialId,
                IfMatchVersion.require(
                        ifMatch,
                        IamErrorCode.TOTP_CREDENTIAL_IF_MATCH_REQUIRED,
                        IamErrorCode.TOTP_CREDENTIAL_CHANGED),
                body.getEnrollmentId(),
                body.getCode(),
                authenticationBrowser.require(request));
        return ApiResponse.ok(TotpCredentialChangeView.from(change), change.getVersion());
    }

    /**
     * 单次消费 STEP_UP 后逻辑删除指定版本 TOTP Credential。
     *
     * @return 204；目标 Account 全部旧登录 Session 已结束
     */
    @DeleteMapping("/credentials/{credentialId}")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "TOTP Credential 已删除")
    @Audit(module = AuthenticationAuditOperations.ACCOUNT_SECURITY, action = AuthenticationAuditOperations.TOTP_REMOVAL, targetType = IamResourceTypes.ACCOUNT, targetId = "#accountId", payload = "T(java.util.Map).of('credentialId', #credentialId)")
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
                        IamErrorCode.TOTP_CREDENTIAL_IF_MATCH_REQUIRED,
                        IamErrorCode.TOTP_CREDENTIAL_CHANGED),
                authenticationId,
                authenticationBrowser.require(request));
        return ResponseEntity.noContent().build();
    }
}
