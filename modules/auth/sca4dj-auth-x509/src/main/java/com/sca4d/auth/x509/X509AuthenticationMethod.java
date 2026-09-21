package com.sca4d.auth.x509;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.AuthenticationMethodCode;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 登记、查询、撤销和验证 Account 的 X.509 公钥证书 Credential。 */
public class X509AuthenticationMethod implements AuthenticationMethod<X509TransportProof> {

    private static final Logger LOG = LoggerFactory.getLogger(X509AuthenticationMethod.class);

    /** X.509 方法稳定编码。 */
    public static final AuthenticationMethodCode CODE = new AuthenticationMethodCode("x509");

    private final @NotNull X509CredentialRepository credentials;

    private final @NotNull AccountDirectory accounts;

    private final @NotNull SessionControl sessions;

    private final @NotNull IdGenerator ids;

    private final @NotNull Clock clock;

    /** 创建从证书指纹识别 Account 的 X.509 认证方法。 */
    public X509AuthenticationMethod(
            @NotNull X509CredentialRepository credentials,
            @NotNull AccountDirectory accounts,
            @NotNull SessionControl sessions,
            @NotNull IdGenerator ids,
            @NotNull Clock clock) {
        this.credentials = Objects.requireNonNull(credentials, "X.509 Credential Repository 不能为空");
        this.accounts = Objects.requireNonNull(accounts, "AccountDirectory 不能为空");
        this.sessions = Objects.requireNonNull(sessions, "Session 生命周期不能为空");
        this.ids = Objects.requireNonNull(ids, "ID 生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "X.509 时钟不能为空");
    }

    @Override
    public @NotNull AuthenticationMethodCode code() {
        return CODE;
    }

    @Override
    public @NotNull Class<X509TransportProof> proofType() {
        return X509TransportProof.class;
    }

    @Override
    public @NotNull Set<AuthenticationFactorKind> factorKinds() {
        return Set.of(AuthenticationFactorKind.POSSESSION);
    }

    @Override
    public boolean canIdentifyAccount() {
        return true;
    }

    @Override
    public boolean available(@NotNull Account account) {
        return credentials.hasUsable(account.accountId(), clock.instant());
    }

    @Override
    public @NotNull VerifiedAuthentication authenticate(
            @Nullable Account expectedAccount,
            @NotNull X509TransportProof proof) {
        try {
            byte[] der = Objects.requireNonNull(proof, "X.509 Transport Proof 不能为空").certificateDer();
            X509Certificate certificate = X509Certificates.parse(der, X509ErrorCode.REJECTED);
            String fingerprint = X509Certificates.fingerprint(der);
            Instant now = clock.instant();
            X509Credential credential = credentials.findUsable(fingerprint, now);
            if (credential == null) {
                throw new BusinessException(X509ErrorCode.REJECTED);
            }
            Account account = accounts.activeById(credential.accountId());
            if (expectedAccount != null && expectedAccount.accountId() != account.accountId()) {
                throw new BusinessException(X509ErrorCode.REJECTED);
            }
            if (!certificate.getNotAfter().toInstant().equals(credential.notAfter())) {
                throw new BusinessException(X509ErrorCode.REJECTED);
            }
            Authentication authentication = new Authentication(credential.accountId(), CODE, factorKinds(), now);
            return new VerifiedAuthentication(
                    account,
                    authentication,
                    Long.toString(credential.id()),
                    credential.version(),
                    credential.notAfter());
        } catch (BusinessException exception) {
            throw new BusinessException(X509ErrorCode.REJECTED, exception);
        }
    }

    @Override
    public void verifyCurrent(@NotNull Account account, @NotNull VerifiedAuthentication authentication) {
        long credentialId;
        try {
            credentialId = Long.parseLong(authentication.reference());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("X.509 认证证据引用无效", exception);
        }
        X509Credential credential = credentials.find(credentialId);
        if (credential == null
                || credential.accountId() != account.accountId()
                || !credential.version().equals(authentication.version())
                || !credential.usableAt(clock.instant())) {
            throw new IllegalStateException("X.509 Credential 已变化");
        }
    }

    /**
     * 为 Account 登记一张外部签发的 X.509 公钥证书。
     *
     * @param account 已经由管理流程确认存在的 Account；Account 停用时仍允许预置证书
     * @param registration 只包含 DER 公钥证书的登记数据
     * @return 已保存且不包含 DER 或私钥的 Credential
    */
    @Transactional
    public @NotNull X509Credential register(
            @NotNull Account account,
            @NotNull X509CredentialRegistration registration) {
        Account checkedAccount = Objects.requireNonNull(account, "Account 不能为空");
        byte[] der = Objects.requireNonNull(registration, "X.509 登记数据不能为空").certificateDer();
        X509Certificate certificate = X509Certificates.parse(der, X509ErrorCode.INVALID_CERTIFICATE);
        Instant now = clock.instant();
        Instant notBefore = certificate.getNotBefore().toInstant();
        Instant notAfter = certificate.getNotAfter().toInstant();
        if (!notAfter.isAfter(now) || !notAfter.isAfter(notBefore)) {
            throw new BusinessException(X509ErrorCode.INVALID_CERTIFICATE);
        }
        X509Credential credential = new X509Credential(
                ids.nextId().value(),
                checkedAccount.accountId(),
                X509Certificates.fingerprint(der),
                certificate.getSerialNumber().toString(16),
                certificate.getSubjectX500Principal().getName(),
                notBefore,
                notAfter,
                X509Credential.Status.ACTIVE,
                new Version(0),
                now);
        try {
            credentials.add(credential, der);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(X509ErrorCode.CREDENTIAL_CONFLICT, exception);
        }
        LOG.info(
                "X.509 Credential 已登记 | accountId={} credentialId={} notAfter={}",
                checkedAccount.accountId(),
                credential.id(),
                credential.notAfter());
        return credential;
    }

    /**
     * 读取 Account 登记过的全部 X.509 Credential。
     *
     * @param accountId Account 标识
     * @return 按登记时间倒序排列的 Credential
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull List<X509Credential> findAll(long accountId) {
        return credentials.findAll(accountId);
    }

    /**
     * 判断 Account 当前是否至少有一张可认证的 X.509 Credential。
     *
     * @param accountId Account 标识
     * @return 存在当前有效 Credential 时为 {@code true}
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public boolean active(long accountId) {
        return credentials.hasUsable(accountId, clock.instant());
    }

    /**
     * 撤销 Account 的指定 X.509 Credential，并终止该 Account 的全部旧 Session。
     *
     * @param accountId Account 标识
     * @param credentialId Credential 标识
     * @return 本次完成撤销时为 {@code true}，已经撤销时为 {@code false}
     */
    @Transactional
    public boolean revoke(long accountId, long credentialId) {
        X509Credential credential = credentials.find(credentialId);
        if (credential == null || credential.accountId() != accountId) {
            throw new BusinessException(X509ErrorCode.CREDENTIAL_NOT_FOUND);
        }
        if (!credential.active()) {
            return false;
        }
        credentials.updateStatus(credential, X509Credential.Status.REVOKED, clock.instant());
        sessions.terminateAccount(accountId, SessionTerminationReason.AUTHENTICATION_CHANGED);
        LOG.info("X.509 Credential 已撤销 | accountId={} credentialId={}", accountId, credentialId);
        return true;
    }

    /**
     * 撤销 Account 的全部有效 X.509 Credential。
     *
     * @param accountId Account 标识
     */
    @Transactional
    public void revokeAll(long accountId) {
        boolean revoked = credentials.updateAllStatuses(
                accountId,
                X509Credential.Status.ACTIVE,
                X509Credential.Status.REVOKED,
                clock.instant());
        if (revoked) {
            sessions.terminateAccount(accountId, SessionTerminationReason.AUTHENTICATION_CHANGED);
            LOG.info("Account 全部 X.509 Credential 已撤销 | accountId={}", accountId);
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void eraseAccount(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        credentials.erase(accountId);
        LOG.debug("X.509 Credential 已擦除 | accountId={}", accountId);
    }
}
