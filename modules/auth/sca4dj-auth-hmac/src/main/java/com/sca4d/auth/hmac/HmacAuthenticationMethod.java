package com.sca4d.auth.hmac;

import static com.sca4d.auth.hmac.HmacCredential.Status.ACTIVE;
import static com.sca4d.auth.hmac.HmacCredential.Status.REVOKED;
import static com.sca4d.auth.hmac.HmacErrorCode.CREDENTIAL_NOT_FOUND;
import static com.sca4d.auth.hmac.HmacErrorCode.REJECTED;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 签发、轮换、撤销和验证 Account 的 MAC-SHA256 Credential，并执行时间窗、防重放和失败限制。
 */
public class HmacAuthenticationMethod implements AuthenticationMethod<HmacProof> {

    /**
     * MAC 方法稳定编码。
     */
    public static final TreeCode CODE = TreeCode.ofSlash("AUTHENTICATION/HMAC");

    private static final Logger LOG = LoggerFactory.getLogger(HmacAuthenticationMethod.class);

    private final @NotNull HmacCredentialRepository credentials;

    private final @NotNull HmacKeyCryptography cryptography;

    private final @NotNull SessionControl sessions;

    private final @NotNull SecureRandom random;

    private final @NotNull HmacAuthenticationAttemptStore attempts;

    private final @NotNull Clock clock;

    private final @NotNull HmacProperties properties;

    /**
     * 创建 Account 级 MAC 认证方法。
     */
    public HmacAuthenticationMethod(
            @NotNull HmacCredentialRepository credentials,
            @NotNull HmacKeyCryptography cryptography,
            @NotNull SessionControl sessions,
            @NotNull SecureRandom random,
            @NotNull HmacAuthenticationAttemptStore attempts,
            @NotNull Clock clock,
            @NotNull HmacProperties properties) {
        this.credentials = Objects.requireNonNull(credentials, "HMAC Credential Repository 不能为空");
        this.cryptography = Objects.requireNonNull(cryptography, "HMAC Key 加密器不能为空");
        this.sessions = Objects.requireNonNull(sessions, "Session 生命周期不能为空");
        this.random = Objects.requireNonNull(random, "安全随机数生成器不能为空");
        this.attempts = Objects.requireNonNull(attempts, "HMAC 认证尝试存储不能为空");
        this.clock = Objects.requireNonNull(clock, "HMAC 时钟不能为空");
        this.properties = Objects.requireNonNull(properties, "HMAC 配置不能为空");
        this.properties.validate();
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public @NotNull Class<HmacProof> proofType() {
        return HmacProof.class;
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
        HmacCredential credential = credentials.find(account.accountId());
        return credential != null && credential.active();
    }

    @Override
    public @NotNull VerifiedAuthentication authenticate(
            @Nullable Account account,
            @NotNull HmacProof proof) {
        if (account == null) {
            throw new BusinessException(REJECTED);
        }
        HmacProof checked = Objects.requireNonNull(proof, "HMAC Proof 不能为空");
        if (Duration.between(checked.timestamp(), clock.instant()).abs().compareTo(properties.getClockSkew()) > 0
                || attempts.failureCount(account.accountId()) >= properties.getFailureLimit()) {
            throw new BusinessException(REJECTED);
        }

        // 先消费 Account 和 nonce，错误签名不能重复使用相同请求材料。
        if (!attempts.consumeNonce(account.accountId(), checked.nonce(), properties.getNonceTtl())) {
            throw new BusinessException(REJECTED);
        }

        HmacCredential credential = credentials.find(account.accountId());
        if (credential == null || !credential.active() || !matches(credential, checked)) {
            recordFailure(account.accountId());
            throw new BusinessException(REJECTED);
        }
        attempts.clearFailures(account.accountId());
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
        HmacCredential credential = credentials.find(account.accountId());
        if (credential == null
                || !credential.active()
                || !Long.toString(account.accountId()).equals(authentication.reference())
                || !credential.version().equals(authentication.version())) {
            throw new IllegalStateException("HMAC Credential 已变化");
        }
    }

    /**
     * 为 Account 创建、轮换或重新签发 MAC Key。
     *
     * @param account 已经由管理流程确认存在的 Account；Account 停用时仍允许预置或轮换 Credential
     * @return 新 Credential 和只在本次调用可见的 Key
     */
    @Transactional
    @CheckReturnValue
    public @NotNull IssuedHmacKey issue(@NotNull Account account) {
        long accountId = Objects.requireNonNull(account, "Account 不能为空").accountId();
        HmacCredential current = credentials.find(accountId);
        long nextVersion = current == null ? 0 : current.version().next().value();
        String plaintext = generate();
        HmacCredential saved = current == null
                ? credentials.create(accountId, cryptography.encrypt(accountId, nextVersion, plaintext))
                : credentials.replace(
                        current,
                        cryptography.encrypt(accountId, nextVersion, plaintext),
                        ACTIVE,
                        clock.instant());
        if (current != null) {
            sessions.terminateAccount(accountId, SessionTerminationReason.AUTHENTICATION_CHANGED);
        }
        LOG.info("HMAC Credential 已签发 | accountId={} version={}", accountId, saved.version().value());
        return new IssuedHmacKey(saved, plaintext);
    }

    /**
     * 判断 Account 是否存在仍有效的 MAC Credential。
     *
     * @param accountId Account 标识
     * @return Credential 存在且未撤销时为 {@code true}
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public boolean active(long accountId) {
        HmacCredential credential = credentials.find(accountId);
        return credential != null && credential.active();
    }

    /**
     * 撤销 Account 当前 MAC Credential。
     *
     * @param accountId Account 标识
     * @param revokedAt 撤销时间
     * @return 本次调用完成撤销时为 {@code true}，已经撤销时为 {@code false}
     */
    @Transactional
    public boolean revoke(long accountId, @NotNull Instant revokedAt) {
        HmacCredential current = credentials.find(accountId);
        if (current == null) {
            throw new BusinessException(CREDENTIAL_NOT_FOUND);
        }
        if (!current.active()) {
            return false;
        }
        boolean revoked = credentials.updateStatus(
                accountId,
                ACTIVE,
                REVOKED,
                Objects.requireNonNull(revokedAt, "撤销时间不能为空"));
        if (revoked) {
            sessions.terminateAccount(accountId, SessionTerminationReason.AUTHENTICATION_CHANGED);
            LOG.info("HMAC Credential 已撤销 | accountId={}", accountId);
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
        attempts.clearFailures(accountId);
        LOG.debug("HMAC Credential 已擦除 | accountId={}", accountId);
    }

    /**
     * 生成 32 字节无填充 Base64URL Key。
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

    /**
     * 使用当前 Credential 解密 Key 并固定时间比较签名。
     */
    private boolean matches(HmacCredential credential, HmacProof proof) {
        byte[] key = cryptography.decrypt(credential);
        byte[] supplied;
        try {
            supplied = Base64.getUrlDecoder().decode(proof.signature());
        } catch (IllegalArgumentException exception) {
            Arrays.fill(key, (byte) 0);
            return false;
        }
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] expected = hmac.doFinal(proof.canonicalBytes());
            try {
                return MessageDigest.isEqual(expected, supplied);
            } finally {
                Arrays.fill(expected, (byte) 0);
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("JVM 不支持 HmacSHA256", exception);
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(supplied, (byte) 0);
        }
    }

    /**
     * 记录失败并在首次失败时设置固定窗口。
     */
    private void recordFailure(long accountId) {
        long count = attempts.recordFailure(accountId, properties.getFailureWindow());
        LOG.debug("HMAC 认证失败已计数 | accountId={} failures={}", accountId, count);
    }

}
