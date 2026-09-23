package com.sca4d.auth.sharedsecret;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.security.SecretHasher;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

/**
 * 签发、轮换、撤销和验证 Account 的 Shared Secret Credential。
 */
public class SharedSecretAuthenticationMethod implements AuthenticationMethod<SharedSecretProof> {

    private static final Logger LOG = LoggerFactory.getLogger(SharedSecretAuthenticationMethod.class);

    /**
     * Shared Secret 方法稳定编码。
     */
    public static final TreeCode CODE = TreeCode.ofSlash("AUTHENTICATION/SHARED_SECRET");

    private final @NotNull SharedSecretCredentialRepository credentials;

    private final @NotNull SecretHasher hasher;

    private final @NotNull SessionControl sessions;

    private final @NotNull SecureRandom random;

    private final @NotNull Clock clock;

    /**
     * 创建只接受已确定 Account 的 Shared Secret 认证方法。
     */
    public SharedSecretAuthenticationMethod(
            @NotNull SharedSecretCredentialRepository credentials,
            @NotNull SecretHasher hasher,
            @NotNull SessionControl sessions,
            @NotNull SecureRandom random,
            @NotNull Clock clock) {
        this.credentials = Objects.requireNonNull(credentials, "Shared Secret Repository 不能为空");
        this.hasher = Objects.requireNonNull(hasher, "SecretHasher 不能为空");
        this.sessions = Objects.requireNonNull(sessions, "Session 生命周期不能为空");
        this.random = Objects.requireNonNull(random, "安全随机数生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "认证时钟不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public @NotNull Class<SharedSecretProof> proofType() {
        return SharedSecretProof.class;
    }

    @Override
    public @NotNull Set<AuthenticationFactorKind> factorKinds() {
        return Set.of(AuthenticationFactorKind.POSSESSION);
    }

    @Override
    public boolean canIdentifyAccount() {
        return false;
    }

    @Override
    public boolean available(@NotNull Account account) {
        SharedSecretCredential credential = credentials.find(account.accountId());
        return credential != null && credential.active();
    }

    @Override
    public @NotNull VerifiedAuthentication authenticate(
            @Nullable Account account,
            @NotNull SharedSecretProof proof) {
        if (account == null) {
            throw new BusinessException(SharedSecretErrorCode.REJECTED);
        }
        SharedSecretCredential credential = credentials.find(account.accountId());
        if (credential == null
                || !credential.active()
                || !hasher.matches(
                        Objects.requireNonNull(proof, "Shared Secret Proof 不能为空").secret(),
                        credential.secretHash())) {
            throw new BusinessException(SharedSecretErrorCode.REJECTED);
        }
        Authentication authentication = new Authentication(
                account.accountId(),
                CODE,
                factorKinds(),
                clock.instant());
        return new VerifiedAuthentication(
                account,
                authentication,
                Long.toString(account.accountId()),
                credential.version(),
                null);
    }

    @Override
    public void verifyCurrent(@NotNull Account account, @NotNull VerifiedAuthentication authentication) {
        SharedSecretCredential credential = credentials.find(account.accountId());
        if (credential == null
                || !credential.active()
                || !Long.toString(account.accountId()).equals(authentication.reference())
                || !credential.version().equals(authentication.version())) {
            throw new IllegalStateException("Shared Secret 凭据已变化");
        }
    }

    /**
     * 为 Account 创建、轮换或重新签发 Shared Secret，并返回一次性原始值。
     *
     * @param account 已经由管理流程确认存在的 Account；Account 停用时仍允许预置或轮换 Credential
     * @return 新 Credential 和只在本次调用可见的 Secret
     */
    @Transactional
    @CheckReturnValue
    public @NotNull IssuedSharedSecret issue(@NotNull Account account) {
        long accountId = Objects.requireNonNull(account, "Account 不能为空").accountId();
        String plaintext = generate();
        SharedSecretCredential current = credentials.find(accountId);
        SharedSecretCredential saved = current == null
                ? credentials.create(accountId, hasher.hash(plaintext))
                : credentials.replace(
                        current,
                        hasher.hash(plaintext),
                        SharedSecretCredential.Status.ACTIVE,
                        clock.instant());
        if (current != null) {
            sessions.terminateAccount(accountId, SessionTerminationReason.AUTHENTICATION_CHANGED);
        }
        LOG.info("Shared Secret Credential 已签发 | accountId={} version={}", accountId, saved.version().value());
        return new IssuedSharedSecret(saved, plaintext);
    }

    /**
     * 判断 Account 是否存在仍有效的 Shared Secret。
     *
     * @param accountId Account 标识
     * @return Credential 存在且未撤销时为 {@code true}
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public boolean active(long accountId) {
        SharedSecretCredential credential = credentials.find(accountId);
        return credential != null && credential.active();
    }

    /**
     * 撤销 Account 当前 Shared Secret。
     *
     * @param accountId Account 标识
     * @param revokedAt 撤销时间
     * @return 本次调用完成撤销时为 {@code true}，已经撤销时为 {@code false}
     */
    @Transactional
    public boolean revoke(long accountId, @NotNull Instant revokedAt) {
        SharedSecretCredential current = credentials.find(accountId);
        if (current == null) {
            throw new BusinessException(SharedSecretErrorCode.CREDENTIAL_NOT_FOUND);
        }
        if (!current.active()) {
            return false;
        }
        boolean revoked = credentials.updateStatus(
                accountId,
                SharedSecretCredential.Status.ACTIVE,
                SharedSecretCredential.Status.REVOKED,
                Objects.requireNonNull(revokedAt, "撤销时间不能为空"));
        if (revoked) {
            sessions.terminateAccount(accountId, SessionTerminationReason.AUTHENTICATION_CHANGED);
            LOG.info("Shared Secret Credential 已撤销 | accountId={}", accountId);
        }
        return revoked;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void eraseAccount(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        credentials.erase(accountId);
        LOG.debug("Shared Secret Credential 已擦除 | accountId={}", accountId);
    }

    /**
     * 生成 32 字节无填充 Base64URL Secret。
     */
    private @NotNull String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
