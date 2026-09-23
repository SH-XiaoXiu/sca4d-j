package com.sca4d.app.server.controllers;

import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.authentication.RequireAuthentication;
import com.sca4d.app.server.authentication.request.TotpCredentialRequest;
import com.sca4d.app.server.authentication.view.TotpCredentialView;
import com.sca4d.app.server.authentication.view.TotpEnrollmentView;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.TotpAccountService;
import com.sca4d.auth.totp.credential.TotpCredential;
import com.sca4d.auth.totp.enrollment.TotpEnrollment;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.authentication.RequireSessionToken;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentAccount;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

/** 管理当前 Account 的 TOTP enrollment、二维码和凭据。 */
@RestController
@RequestMapping("/me/authentication/totp")
@Tag(name = "Account Authentication")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class TotpController {

    private final TotpAccountService totp;

    /**
     * 创建当前 Account TOTP 入口。
     *
     * @param totp 当前 Account TOTP 应用服务
     */
    public TotpController(@NotNull TotpAccountService totp) {
        this.totp = Objects.requireNonNull(totp, "TOTP 应用服务不能为空");
    }

    /**
     * 查询当前 Account 是否启用 TOTP。
     *
     * @param accountId 当前 Account 标识
     * @return 不包含 Secret 与重放游标的凭据状态
     */
    @GetMapping("/credential")
    @Operation(summary = "查询当前 Account TOTP 状态")
    public @NotNull ApiResponse<TotpCredentialView> credential(
            @CurrentAccount @NotNull Long accountId) {
        TotpCredential credential = totp.credentialByAccountId(accountId);
        return ApiResponse.ok(TotpCredentialView.from(credential));
    }

    /**
     * 为已经通过动作认证的当前账号创建新的短期 enrollment。
     *
     * @param accountId 当前 Account 标识
     * @return enrollment 元数据及二维码 Location
     */
    @PostMapping("/enrollments")
    @RequireAuthentication(AuthenticationActions.TOTP_ENROLL_CODE)
    @Operation(summary = "创建 TOTP enrollment")
    public @NotNull ApiResponse<TotpEnrollmentView> createEnrollment(
            @CurrentAccount @NotNull Long accountId) {
        TotpEnrollment enrollment = totp.createEnrollment(accountId);
        URI location = URI.create("/me/authentication/totp/enrollments/" + enrollment.enrollmentId());
        return ApiResponse.created(TotpEnrollmentView.from(enrollment), location);
    }

    /**
     * 读取当前 Account 仍可使用的 enrollment。
     *
     * @param accountId 当前 Account 标识
     * @param enrollmentId enrollment 标识
     * @return enrollment 元数据
     */
    @GetMapping("/enrollments/{enrollmentId}")
    @Operation(summary = "查询 TOTP enrollment")
    public @NotNull ApiResponse<TotpEnrollmentView> enrollment(
            @CurrentAccount @NotNull Long accountId,
            @PathVariable @Size(max = 64) @NotNull String enrollmentId) {
        return ApiResponse.ok(TotpEnrollmentView.from(totp.enrollmentById(accountId, enrollmentId)));
    }

    /**
     * 取消当前 Account 仍可使用的 enrollment。
     *
     * @param accountId 当前 Account 标识
     * @param enrollmentId enrollment 标识
     * @return 空成功响应
     */
    @DeleteMapping("/enrollments/{enrollmentId}")
    @Operation(summary = "取消 TOTP enrollment")
    public @NotNull ApiResponse<Void> cancelEnrollment(
            @CurrentAccount @NotNull Long accountId,
            @PathVariable @Size(max = 64) @NotNull String enrollmentId) {
        totp.cancelEnrollment(accountId, enrollmentId);
        return ApiResponse.noContent();
    }

    /**
     * 返回禁止缓存的 Hutool PNG，不在 JSON 中暴露 provisioning URI。
     *
     * @param accountId 当前 Account 标识
     * @param enrollmentId 当前 Account 的 enrollment 标识
     * @return 带 no-store 响应头的 PNG
     */
    @GetMapping(value = "/enrollments/{enrollmentId}/qr-code", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "读取 TOTP enrollment 二维码")
    public @NotNull ResponseEntity<byte[]> qrCode(
            @CurrentAccount @NotNull Long accountId,
            @PathVariable @Size(max = 64) @NotNull String enrollmentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(totp.qrCode(accountId, enrollmentId));
    }

    /**
     * 使用 enrollment 的首次验证码激活 TOTP。
     *
     * @param accountId 当前 Account 标识
     * @param request enrollment 标识与首次验证码
     * @return 已激活凭据的安全投影
     */
    @PostMapping("/credential")
    @Operation(summary = "激活 TOTP Credential")
    @Audit(operation = "authentication/account/totp/activate")
    public @NotNull ApiResponse<TotpCredentialView> activate(
            @CurrentAccount @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) @NotNull Long accountId,
            @RequestBody @NotNull TotpCredentialRequest request) {
        TotpCredential credential = totp.activate(accountId, request.getEnrollmentId(), request.getCode());
        return ApiResponse.created(
                TotpCredentialView.from(credential),
                URI.create("/me/authentication/totp/credential"));
    }

    /**
     * 使用新 enrollment 替换 TOTP。
     *
     * @param accountId 当前 Account 标识
     * @param request 新 enrollment 标识与首次验证码
     * @return 替换后凭据的安全投影
     */
    @PutMapping("/credential")
    @RequireAuthentication(AuthenticationActions.TOTP_REPLACE_CODE)
    @Operation(summary = "替换 TOTP Credential")
    @Audit(operation = "authentication/account/totp/replace")
    public @NotNull ApiResponse<TotpCredentialView> replace(
            @CurrentAccount @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) @NotNull Long accountId,
            @RequestBody @NotNull TotpCredentialRequest request) {
        TotpCredential credential = totp.replace(
                accountId,
                request.getEnrollmentId(),
                request.getCode());
        return ApiResponse.ok(TotpCredentialView.from(credential));
    }

    /**
     * 删除不会令账号认证设置失效的 TOTP。
     *
     * @param account 当前 Account
     * @return 空成功响应
     */
    @DeleteMapping("/credential")
    @RequireSessionToken
    @RequireAuthentication(AuthenticationActions.TOTP_REPLACE_CODE)
    @Operation(summary = "删除 TOTP Credential")
    @Audit(operation = "authentication/account/totp/delete")
    public @NotNull ApiResponse<Void> delete(
            @CurrentAccount @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE) @NotNull Account account) {
        totp.delete(account);
        return ApiResponse.noContent();
    }
}
