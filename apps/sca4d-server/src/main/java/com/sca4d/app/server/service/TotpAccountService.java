package com.sca4d.app.server.service;

import com.sca4d.app.server.ProductSettings;
import com.sca4d.app.server.authentication.TotpQrCodeGenerator;
import com.sca4d.auth.totp.TotpAuthenticationMethod;
import com.sca4d.auth.totp.TotpErrorCode;
import com.sca4d.auth.totp.credential.TotpCredential;
import com.sca4d.auth.totp.credential.TotpCredentialRepository;
import com.sca4d.auth.totp.enrollment.TotpEnrollment;
import com.sca4d.auth.totp.enrollment.TotpEnrollmentService;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 组合 TOTP 模块与示例应用认证规则，限定所有自助操作属于当前 Account。 */
@Service
public class TotpAccountService {

    private final TotpEnrollmentService enrollments;

    private final TotpCredentialRepository credentials;

    private final PlatformAuthenticationPolicyService policies;

    private final TotpQrCodeGenerator qrCodes;

    private final ProductSettings settings;

    /**
     * 创建当前 Account TOTP 应用服务。
     *
     * @param enrollments TOTP enrollment 生命周期服务
     * @param credentials TOTP 凭据 Repository
     * @param policies 示例应用认证策略服务
     * @param qrCodes App 二维码生成器
     * @param settings 每次生成二维码时读取 issuer 的 Settings
     */
    public TotpAccountService(
            @NotNull TotpEnrollmentService enrollments,
            @NotNull TotpCredentialRepository credentials,
            @NotNull PlatformAuthenticationPolicyService policies,
            @NotNull TotpQrCodeGenerator qrCodes,
            @NotNull ProductSettings settings) {
        this.enrollments = Objects.requireNonNull(enrollments, "TOTP enrollment 服务不能为空");
        this.credentials = Objects.requireNonNull(credentials, "TOTP 凭据 Repository 不能为空");
        this.policies = Objects.requireNonNull(policies, "应用认证策略服务不能为空");
        this.qrCodes = Objects.requireNonNull(qrCodes, "TOTP 二维码生成器不能为空");
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
    }

    /**
     * 为已经通过接口动作认证的当前账号创建短期加密 enrollment。
     *
     * @param accountId 当前 Account 标识
     * @return 不包含 Secret 的 enrollment 元数据
     */
    public @NotNull TotpEnrollment createEnrollment(@NotNull Long accountId) {
        return enrollments.create(Objects.requireNonNull(accountId, "Account 标识不能为空"));
    }

    /**
     * 把瞬时 provisioning URI 直接转为 PNG，不返回或保存 URI。
     *
     * @param accountId 当前 Account 标识
     * @param enrollmentId 当前 Account 的 enrollment 标识
     * @return 只供当前响应使用的 PNG 字节
     */
    public byte @NotNull [] qrCode(@NotNull Long accountId, @NotNull String enrollmentId) {
        Long checkedAccountId = Objects.requireNonNull(accountId, "Account 标识不能为空");
        String uri = enrollments.provisioningUri(
                checkedAccountId,
                enrollmentId,
                settings.TOTP_ISSUER.get(),
                "account-" + checkedAccountId);
        return qrCodes.generate(uri);
    }

    /**
     * 查询当前 Account 的 TOTP 凭据。
     *
     * @param accountId 当前 Account 标识
     * @return 当前 Account TOTP 凭据；未启用时为空
     */
    public @NotNull TotpCredential credentialByAccountId(@NotNull Long accountId) {
        TotpCredential credential = credentials.findByAccountId(
                Objects.requireNonNull(accountId, "Account 标识不能为空"));
        if (credential == null) {
            throw new BusinessException(TotpErrorCode.CREDENTIAL_UNAVAILABLE);
        }
        return credential;
    }

    /**
     * 读取当前 Account 仍可使用的 enrollment。
     *
     * @param accountId 当前 Account 标识
     * @param enrollmentId enrollment 不透明标识
     * @return 不包含 Secret 的 enrollment 元数据
     */
    public @NotNull TotpEnrollment enrollmentById(
            @NotNull Long accountId,
            @NotNull String enrollmentId) {
        return enrollments.loadById(
                Objects.requireNonNull(accountId, "Account 标识不能为空"),
                enrollmentId);
    }

    /**
     * 取消当前 Account 仍可使用的 enrollment。
     *
     * @param accountId 当前 Account 标识
     * @param enrollmentId enrollment 不透明标识
     */
    public void cancelEnrollment(@NotNull Long accountId, @NotNull String enrollmentId) {
        enrollments.cancel(
                Objects.requireNonNull(accountId, "Account 标识不能为空"),
                enrollmentId);
    }

    /**
     * 使用 enrollment 首次验证码激活 TOTP。
     *
     * @param accountId 当前 Account 标识
     * @param enrollmentId 当前 Account 的 enrollment 标识
     * @param code 首次六位验证码
     * @return 已激活凭据
     */
    public @NotNull TotpCredential activate(
            @NotNull Long accountId,
            @NotNull String enrollmentId,
            @NotNull String code) {
        return enrollments.activate(
                Objects.requireNonNull(accountId, "Account 标识不能为空"),
                enrollmentId,
                code);
    }

    /**
     * 使用新 enrollment 替换 TOTP。
     *
     * @param accountId 当前 Account 标识
     * @param enrollmentId 新 enrollment 标识
     * @param code 新 Secret 的首次六位验证码
     * @return 替换后的凭据
     */
    public @NotNull TotpCredential replace(
            @NotNull Long accountId,
            @NotNull String enrollmentId,
            @NotNull String code) {
        return enrollments.replace(
                Objects.requireNonNull(accountId, "Account 标识不能为空"),
                enrollmentId,
                code);
    }

    /**
     * 确认删除后账号认证设置仍可满足，再删除 TOTP。
     *
     * @param account 当前 Account
     */
    public void delete(@NotNull Account account) {
        Account checked = Objects.requireNonNull(account, "Account 不能为空");
        policies.assertMethodRemovalAllowed(checked, TotpAuthenticationMethod.CODE);
        enrollments.delete(checked.accountId());
    }

}
