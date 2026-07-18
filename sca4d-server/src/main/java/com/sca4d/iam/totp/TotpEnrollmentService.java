package com.sca4d.iam.totp;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import org.jetbrains.annotations.NotNull;

/** 创建短期 TOTP enrollment，并激活、替换或删除 Account 的 TOTP Credential。 */
public interface TotpEnrollmentService {

    /**
     * 单次消费当前 Session Account 的 STEP_UP 并创建浏览器绑定 enrollment。
     *
     * @return 只包含 enrollment 标识和过期时间的短期设置资源
     */
    @NotNull
    TotpEnrollment create(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull String authenticationId,
            @NotNull String browserHash);

    /**
     * 读取当前浏览器未消费 enrollment 的 otpauth PNG 二维码。
     *
     * @return 不得缓存、记录或持久化到客户端状态的 PNG 字节
     */
    byte @NotNull [] qrCode(
            @NotNull Long subjectId,
            @NotNull Long accountId,
            @NotNull String enrollmentId,
            @NotNull String browserHash);

    /** 使用 enrollment 的首次验证码创建 ACTIVE TOTP Credential。 */
    @NotNull
    TotpCredentialChange activate(
            @NotNull Long subjectId,
            @NotNull Long accountId,
            @NotNull String enrollmentId,
            @NotNull String code,
            @NotNull String browserHash);

    /** 使用 enrollment 的首次验证码替换指定版本 TOTP Credential。 */
    @NotNull
    TotpCredentialChange replace(
            @NotNull Long subjectId,
            @NotNull Long accountId,
            @NotNull Long credentialId,
            @NotNull Long version,
            @NotNull String enrollmentId,
            @NotNull String code,
            @NotNull String browserHash);

    /** 单次消费 STEP_UP 后逻辑删除 TOTP Credential 并结束目标 Account 全部 Session。 */
    void delete(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull Long credentialId,
            @NotNull Long version,
            @NotNull String authenticationId,
            @NotNull String browserHash);
}
