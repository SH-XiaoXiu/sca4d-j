package com.sca4d.auth.totp.enrollment;

import com.sca4d.auth.totp.TotpAuthenticationMethod;
import com.sca4d.auth.totp.TotpErrorCode;
import com.sca4d.auth.totp.config.TotpSettings;
import com.sca4d.auth.totp.credential.TotpCodeVerifier;
import com.sca4d.auth.totp.credential.TotpCredential;
import com.sca4d.auth.totp.credential.TotpCredentialRepository;
import com.sca4d.auth.totp.credential.TotpSecretCryptography;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.setting.Settings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** 管理 TOTP enrollment、激活、替换和删除，不提供 HTTP 或二维码图片类型。 */
public class TotpEnrollmentService {

    private static final Logger LOG = LoggerFactory.getLogger(TotpEnrollmentService.class);

    private final RedisTotpEnrollmentStore enrollments;

    private final TotpCredentialRepository credentials;

    private final TotpCodeVerifier verifier;

    private final TotpSecretCryptography secrets;

    private final SessionControl sessions;

    private final Settings settings;

    private final Clock clock;

    /**
     * 创建 TOTP 凭据生命周期服务。
     *
     * @param enrollments Redis enrollment store
     * @param credentials TOTP 凭据 Repository
     * @param verifier TOTP 密钥生成与验证码校验器
     * @param keyring Secret 加解密能力
     * @param sessions 账号 Session 终止能力
     * @param settings 每次创建 enrollment 时读取有效期的 Settings
     * @param clock TTL 与验证码校验的时间来源
     */
    public TotpEnrollmentService(
            @NotNull RedisTotpEnrollmentStore enrollments,
            @NotNull TotpCredentialRepository credentials,
            @NotNull TotpCodeVerifier verifier,
            @NotNull SecretKeyring keyring,
            @NotNull SessionControl sessions,
            @NotNull Settings settings,
            @NotNull Clock clock) {
        this.enrollments = Objects.requireNonNull(enrollments, "TOTP enrollment store 不能为空");
        this.credentials = Objects.requireNonNull(credentials, "TOTP 凭据 Repository 不能为空");
        this.verifier = Objects.requireNonNull(verifier, "TOTP 验证器不能为空");
        this.secrets = new TotpSecretCryptography(keyring);
        this.sessions = Objects.requireNonNull(sessions, "Account Session 终止能力不能为空");
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 创建 TTL 约束且只保存加密 Secret 的 enrollment。
     *
     * @param accountId enrollment 所属账号
     * @return 新建的 enrollment 元数据
     */
    public @NotNull TotpEnrollment create(long accountId) {
        String enrollmentId = UUID.randomUUID().toString();
        Instant expiresAt = clock.instant().plus(settings.get(TotpSettings.ENROLLMENT_TTL));
        String secret = verifier.generateSecret();
        TotpEnrollmentState state = new TotpEnrollmentState(
                enrollmentId,
                accountId,
                secrets.encrypt(enrollmentPurpose(enrollmentId), secret),
                expiresAt,
                TotpEnrollmentStatus.PENDING,
                0);
        enrollments.create(state);
        LOG.info("TOTP 配置流程已创建 | accountId={} enrollmentId={} expiresAt={}", accountId, enrollmentId, expiresAt);
        return state.enrollment();
    }

    /**
     * 读取 Account 当前仍可使用的 enrollment。
     *
     * @param accountId enrollment 所属账号
     * @param enrollmentId enrollment 不透明标识
     * @return 不包含 Secret 的 enrollment 元数据
     */
    public @NotNull TotpEnrollment loadById(
            long accountId,
            @NotNull String enrollmentId) {
        return ownedEnrollment(accountId, enrollmentId).enrollment();
    }

    /**
     * 取消 Account 当前仍可使用的 enrollment。
     *
     * @param accountId enrollment 所属账号
     * @param enrollmentId enrollment 不透明标识
     */
    public void cancel(long accountId, @NotNull String enrollmentId) {
        TotpEnrollmentState enrollment = ownedEnrollment(accountId, enrollmentId);
        enrollments.delete(enrollment);
        LOG.debug(
                "TOTP 配置流程已取消 | accountId={} enrollmentId={}",
                accountId,
                enrollmentId);
    }

    /**
     * 返回由可以用于生成二维码的 otpauth URI。
     *
     * <p>调用方不得把 URI 写入响应 JSON、日志或持久化存储。
     *
     * @param accountId enrollment 所属账号
     * @param enrollmentId enrollment 不透明标识
     * @param issuer 身份验证器显示的发行方
     * @param accountLabel 身份验证器显示的账号标签
     * @return Google Authenticator 兼容的 otpauth URI
     */
    public @NotNull String provisioningUri(
            long accountId,
            @NotNull String enrollmentId,
            @NotNull String issuer,
            @NotNull String accountLabel) {
        TotpEnrollmentState enrollment = ownedEnrollment(accountId, enrollmentId);
        String secret = secrets.decrypt(enrollmentPurpose(enrollment.id()), enrollment.secret());
        return TotpProvisioningUri.create(issuer, accountLabel, secret);
    }

    /**
     * 使用首次验证码激活凭据；该时间步在刷新前仍可用于认证。
     *
     * @param accountId enrollment 与新凭据所属账号
     * @param enrollmentId 待消费的 enrollment 标识
     * @param code 身份验证器当前显示的六位验证码
     * @return 激活后的 TOTP 凭据
     */
    @Transactional
    public @NotNull TotpCredential activate(
            long accountId,
            @NotNull String enrollmentId,
            @NotNull String code) {
        TotpEnrollmentState enrollment = ownedEnrollment(accountId, enrollmentId);
        String secret = secrets.decrypt(enrollmentPurpose(enrollment.id()), enrollment.secret());
        long acceptedStep = requireAcceptedStep(secret, code);
        enrollments.consume(enrollment);
        EncryptedSecret credentialSecret = secrets
                .encrypt(TotpAuthenticationMethod.credentialPurpose(accountId), secret);
        TotpCredential credential = credentials.create(accountId, credentialSecret, acceptedStep);
        LOG.debug("TOTP 凭据激活已写入事务 | accountId={} version={}", accountId, credential.version().value());
        return credential;
    }

    /**
     * 使用新 enrollment 替换凭据，并终止目标账号全部旧 Session。
     *
     * @param accountId enrollment 与凭据所属账号
     * @param enrollmentId 待消费的新 enrollment 标识
     * @param code 身份验证器当前显示的六位验证码
     * @return 替换后的 TOTP 凭据
     */
    @Transactional
    public @NotNull TotpCredential replace(
            long accountId,
            @NotNull String enrollmentId,
            @NotNull String code) {
        TotpCredential current = Optional.ofNullable(credentials.findByAccountId(accountId))
                .orElseThrow(() -> new BusinessException(TotpErrorCode.CREDENTIAL_UNAVAILABLE));
        TotpEnrollmentState enrollment = ownedEnrollment(accountId, enrollmentId);
        String secret = secrets.decrypt(enrollmentPurpose(enrollment.id()), enrollment.secret());
        long acceptedStep = requireAcceptedStep(secret, code);
        enrollments.consume(enrollment);
        EncryptedSecret credentialSecret = secrets
                .encrypt(TotpAuthenticationMethod.credentialPurpose(accountId), secret);
        TotpCredential credential = credentials.replace(
                accountId,
                credentialSecret,
                acceptedStep,
                current.version());
        sessions.terminateAccount(accountId, SessionTerminationReason.AUTHENTICATION_CHANGED);
        LOG.debug(
                "TOTP 凭据替换和旧 Session 终止已写入事务 | accountId={} version={}",
                accountId,
                credential.version().value());
        return credential;
    }

    /**
     * 删除凭据，并终止目标账号全部旧 Session。
     *
     * @param accountId 凭据所属账号
     */
    @Transactional
    public void delete(long accountId) {
        TotpCredential current = Optional.ofNullable(credentials.findByAccountId(accountId))
                .orElseThrow(() -> new BusinessException(TotpErrorCode.CREDENTIAL_UNAVAILABLE));
        credentials.delete(accountId, current.version());
        sessions.terminateAccount(accountId, SessionTerminationReason.AUTHENTICATION_CHANGED);
        LOG.debug("TOTP 凭据删除和旧 Session 终止已写入事务 | accountId={}", accountId);
    }

    private TotpEnrollmentState ownedEnrollment(long accountId, String enrollmentId) {
        TotpEnrollmentState enrollment = enrollments.loadById(enrollmentId);
        if (accountId != enrollment.accountId()) {
            throw new BusinessException(TotpErrorCode.ENROLLMENT_UNAVAILABLE);
        }
        return enrollment;
    }

    private long requireAcceptedStep(String secret, String code) {
        OptionalLong matched = verifier.matchStep(secret, code, clock.instant());
        if (matched.isEmpty()) {
            throw new BusinessException(TotpErrorCode.CODE_REJECTED);
        }
        return matched.getAsLong();
    }

    private static String enrollmentPurpose(String enrollmentId) {
        return "totp.enrollment." + enrollmentId + ".secret";
    }

}
