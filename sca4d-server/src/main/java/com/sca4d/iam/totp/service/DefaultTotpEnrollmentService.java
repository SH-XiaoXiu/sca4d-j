package com.sca4d.iam.totp.service;

import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.TotpCredential;
import com.sca4d.common.concurrent.lock.DistributedLock;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountSecondFactorAvailability;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.totp.TotpCodeVerifier;
import com.sca4d.iam.totp.TotpCredentialChange;
import com.sca4d.iam.totp.TotpEnrollment;
import com.sca4d.iam.totp.TotpEnrollmentService;
import com.sca4d.iam.totp.TotpEnrollmentState;
import com.sca4d.iam.totp.TotpQrCodeGenerator;
import com.sca4d.iam.totp.repository.TotpEnrollmentRepository;
import com.sca4d.iam.totp.repository.TotpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** 当前 Subject 的 TOTP enrollment 和 Credential 自助管理实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultTotpEnrollmentService implements TotpEnrollmentService {

    private final AccountService accounts;
    private final CredentialService credentials;
    private final AccountSecondFactorAvailability secondFactors;
    private final AuthenticationProcessService authentications;
    private final Authentication authentication;
    private final TotpEnrollmentRepository enrollments;
    private final TotpRepository totpRepository;
    private final TotpCodeVerifier codes;
    private final TotpQrCodeGenerator qrCodes;
    private final SecretKeyring keyring;
    private final Sca4djAuthenticationProperties properties;

    @Override
    public @NotNull TotpEnrollment create(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        requireOwnedAccount(subject.getSubjectId(), targetAccountId);
        // enrollment 包含新的共享密钥，只有当前 Session Account 完成有效 STEP_UP 后才能创建。
        authentications.authorizeStepUp(
                authenticationId, browserHash, subject, authenticatedAccount);
        String enrollmentId = UUID.randomUUID().toString();
        String secret = codes.generateSecret();
        EncryptedSecret encrypted = keyring.encrypt(
                enrollmentPurpose(enrollmentId), secret);
        Instant now = Instant.now();
        TotpEnrollment enrollment = new TotpEnrollment()
                .setEnrollmentId(enrollmentId)
                .setState(TotpEnrollmentState.PENDING)
                .setVersion(0L)
                .setSubjectId(subject.getSubjectId())
                .setAccountId(targetAccountId)
                .setBrowserHash(browserHash)
                .setSecretKeyId(encrypted.getKeyId())
                .setSecretNonce(Base64.getEncoder().encodeToString(encrypted.getNonce()))
                .setSecretCiphertext(Base64.getEncoder().encodeToString(encrypted.getCiphertext()))
                .setCreatedAt(now)
                .setExpiresAt(now.plus(properties.getTotp().getEnrollmentTtl()));
        enrollments.create(enrollment, properties.getTotp().getEnrollmentTtl());
        log.info(
                "TOTP enrollment 已创建 | subjectId={} authenticatedAccountId={} targetAccountId={} enrollmentId={} expiresAt={}",
                subject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId,
                enrollmentId,
                enrollment.getExpiresAt());
        return enrollment;
    }

    @Override
    public byte @NotNull [] qrCode(
            @NotNull Long subjectId,
            @NotNull Long accountId,
            @NotNull String enrollmentId,
            @NotNull String browserHash) {
        requireOwnedAccount(subjectId, accountId);
        TotpEnrollment enrollment = requirePendingEnrollment(
                subjectId, accountId, enrollmentId, browserHash);
        String secret = keyring.decrypt(
                enrollmentPurpose(enrollmentId), enrollment.encryptedSecret());
        return qrCodes.generate(
                properties.getTotp().getIssuer(), "account-" + accountId, secret);
    }

    @Override
    @DistributedLock(key = "'subject:' + #subjectId")
    @Transactional
    public @NotNull TotpCredentialChange activate(
            @NotNull Long subjectId,
            @NotNull Long accountId,
            @NotNull String enrollmentId,
            @NotNull String code,
            @NotNull String browserHash) {
        requireOwnedAccount(subjectId, accountId);
        TotpEnrollment enrollment = requirePendingEnrollment(
                subjectId, accountId, enrollmentId, browserHash);
        String secret = keyring.decrypt(
                enrollmentPurpose(enrollmentId), enrollment.encryptedSecret());
        Long acceptedStep = codes.matchStep(secret, code, Instant.now());
        if (acceptedStep == null) {
            throw new BizException(AuthenticationErrorCode.TOTP_CODE_INVALID);
        }
        // Redis CAS 先永久消费 enrollment；后续数据库失败时不能用相同设置请求重放共享密钥。
        Long expectedVersion = enrollment.getVersion();
        enrollment.consume();
        if (!enrollments.consume(enrollment, expectedVersion)) {
            throw new BizException(IamErrorCode.TOTP_ENROLLMENT_CHANGED);
        }
        // Credential 写入在 Subject 锁内重新读取 Account，避免设置期间账号被删除。
        requireOwnedAccount(subjectId, accountId);
        Long credentialId = credentials.bind(
                accountId, new TotpCredential(secret, acceptedStep));
        log.info(
                "TOTP Credential 激活已写入事务 | subjectId={} accountId={} credentialId={}",
                subjectId,
                accountId,
                credentialId);
        return new TotpCredentialChange(credentialId, 0L);
    }

    @Override
    @DistributedLock(key = "'subject:' + #subjectId")
    @Transactional
    public @NotNull TotpCredentialChange replace(
            @NotNull Long subjectId,
            @NotNull Long accountId,
            @NotNull Long credentialId,
            @NotNull Long version,
            @NotNull String enrollmentId,
            @NotNull String code,
            @NotNull String browserHash) {
        requireOwnedAccount(subjectId, accountId);
        TotpEnrollment enrollment = requirePendingEnrollment(
                subjectId, accountId, enrollmentId, browserHash);
        String secret = keyring.decrypt(
                enrollmentPurpose(enrollmentId), enrollment.encryptedSecret());
        Long acceptedStep = codes.matchStep(secret, code, Instant.now());
        if (acceptedStep == null) {
            throw new BizException(AuthenticationErrorCode.TOTP_CODE_INVALID);
        }
        // enrollment 只能激活一次；Credential 版本不匹配时保持已消费，调用方必须重新扫描新二维码。
        Long expectedVersion = enrollment.getVersion();
        enrollment.consume();
        if (!enrollments.consume(enrollment, expectedVersion)) {
            throw new BizException(IamErrorCode.TOTP_ENROLLMENT_CHANGED);
        }
        // 替换在同一事务与 Subject 锁内完成。
        requireOwnedAccount(subjectId, accountId);
        credentials.replace(
                accountId,
                credentialId,
                version,
                new TotpCredential(secret, acceptedStep));
        log.info(
                "TOTP Credential 替换已写入事务 | subjectId={} accountId={} credentialId={} version={}",
                subjectId,
                accountId,
                credentialId,
                version + 1);
        return new TotpCredentialChange(credentialId, version + 1);
    }

    @Override
    @DistributedLock(key = "'subject:' + #subject.subjectId")
    @Transactional
    public void delete(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull Long credentialId,
            @NotNull Long version,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        // 锁内重新读取 Account，并排除即将删除的 Credential 复核剩余第二因素。
        IamAccountEntity targetAccount = requireOwnedAccount(
                subject.getSubjectId(), targetAccountId);
        if (Boolean.TRUE.equals(targetAccount.getSecondFactorRequired())
                && !secondFactors.availableAfterRemovingCredential(targetAccount, credentialId)) {
            throw new BizException(IamErrorCode.TOTP_REMOVAL_WOULD_LOCK_ACCOUNT);
        }
        // 可用性复核与类型限定删除共用 Subject 锁。
        authentications.authorizeStepUp(
                authenticationId, browserHash, subject, authenticatedAccount);
        totpRepository.delete(credentialId, targetAccountId, version);
        log.info(
                "TOTP Credential 删除已写入事务 | subjectId={} authenticatedAccountId={} targetAccountId={} credentialId={}",
                subject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId,
                credentialId);
    }

    /**
     * 读取未过期、未消费且属于当前 Subject、Account 和浏览器的 enrollment。
     *
     * @throws BizException 任一归属或状态不匹配时使用相同未找到错误，避免泄漏其他用户设置资源
     */
    private @NotNull TotpEnrollment requirePendingEnrollment(
            @NotNull Long subjectId,
            @NotNull Long accountId,
            @NotNull String enrollmentId,
            @NotNull String browserHash) {
        TotpEnrollment enrollment = enrollments.find(enrollmentId);
        boolean sameBrowser = enrollment != null
                && MessageDigest.isEqual(
                        enrollment.getBrowserHash().getBytes(StandardCharsets.UTF_8),
                        browserHash.getBytes(StandardCharsets.UTF_8));
        if (enrollment == null
                || enrollment.getState() != TotpEnrollmentState.PENDING
                || enrollment.getExpiresAt().isBefore(Instant.now())
                || !subjectId.equals(enrollment.getSubjectId())
                || !accountId.equals(enrollment.getAccountId())
                || !sameBrowser) {
            throw new BizException(IamErrorCode.TOTP_ENROLLMENT_UNAVAILABLE);
        }
        return enrollment;
    }

    /** @return 当前 Subject 名下未删除 Account */
    private @NotNull IamAccountEntity requireOwnedAccount(
            @NotNull Long subjectId, @NotNull Long accountId) {
        IamAccountEntity account = accounts.require(accountId);
        if (!subjectId.equals(account.getSubjectId())) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    /** @return 将 enrollment UUID 写入 GCM additional authenticated data 的用途编码 */
    private @NotNull String enrollmentPurpose(@NotNull String enrollmentId) {
        return "totp.enrollment." + enrollmentId + ".secret";
    }
}
