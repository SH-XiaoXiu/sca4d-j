package com.sca4d.auth.totp;

import com.sca4d.auth.totp.credential.TotpCodeVerifier;
import com.sca4d.auth.totp.credential.TotpCredential;
import com.sca4d.auth.totp.credential.TotpCredentialRepository;
import com.sca4d.auth.totp.credential.TotpSecretCryptography;
import com.sca4d.auth.totp.enrollment.RedisTotpEnrollmentStore;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.security.SecretKeyring;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 验证 Google Authenticator 兼容 TOTP 验证码，并原子拒绝旧时间步。
 */
public class TotpAuthenticationMethod implements AuthenticationMethod<TotpProof> {

    private static final Logger LOG = LoggerFactory.getLogger(TotpAuthenticationMethod.class);

    /**
     * TOTP 方法稳定编码。
     */
    public static final TreeCode CODE = TreeCode.ofSlash("AUTHENTICATION/TOTP");

    private static final Set<AuthenticationFactorKind> FACTOR_KINDS = Set.of(AuthenticationFactorKind.POSSESSION);

    private final TotpCredentialRepository credentials;

    private final Clock clock;

    private final RedisTotpEnrollmentStore enrollments;

    private final TotpCodeVerifier verifier;

    private final TotpSecretCryptography secrets;

    /**
     * 创建 TOTP 认证方法。
     *
     * @param credentials TOTP 凭据 Repository
     * @param enrollments Redis enrollment store
     * @param verifier    TOTP 验证码校验器
     * @param keyring     Secret 解密能力
     * @param clock       认证时间来源
     */
    public TotpAuthenticationMethod(
            @NotNull TotpCredentialRepository credentials,
            @NotNull RedisTotpEnrollmentStore enrollments,
            @NotNull TotpCodeVerifier verifier,
            @NotNull SecretKeyring keyring,
            @NotNull Clock clock) {
        this.credentials = Objects.requireNonNull(credentials, "TOTP 凭据 Repository 不能为空");
        this.enrollments = Objects.requireNonNull(enrollments, "TOTP enrollment store 不能为空");
        this.verifier = Objects.requireNonNull(verifier, "TOTP 验证器不能为空");
        this.secrets = new TotpSecretCryptography(keyring);
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public @NotNull Class<TotpProof> proofType() {
        return TotpProof.class;
    }

    @Override
    public @NotNull Set<AuthenticationFactorKind> factorKinds() {
        return FACTOR_KINDS;
    }

    @Override
    public boolean canIdentifyAccount() {
        return false;
    }

    @Override
    public boolean available(@NotNull Account account) {
        return credentials.findByAccountId(account.accountId()) != null;
    }

    @Override
    public @NotNull VerifiedAuthentication authenticate(
            @Nullable Account account,
            @NotNull TotpProof proof) {
        if (account == null) {
            throw new IllegalArgumentException("TOTP 不能发现 Account");
        }
        TotpCredential credential = credentials.findByAccountId(account.accountId());
        if (credential == null) {
            throw new BusinessException(TotpErrorCode.CODE_REJECTED);
        }
        // Secret 只在本次调用的内存中解密；日志、验证结果和认证尝试都不保存明文。
        String secret = secrets.decrypt(credentialPurpose(account.accountId()), credential.secret());
        Instant authenticatedAt = clock.instant();
        OptionalLong matched = verifier.matchStep(secret, proof.code(), authenticatedAt);
        // 同一条 SQL 同时检查凭据版本和 lastAcceptedStep。当前时间步可重复使用，更旧时间步被跨实例拒绝。
        // 对验证码错误、过时验证码和并发替换返回相同业务错误，避免泄露凭据状态。
        if (matched.isEmpty()
                || !credentials.advanceAcceptedStep(account.accountId(), credential.version(), matched.getAsLong())) {
            throw new BusinessException(TotpErrorCode.CODE_REJECTED);
        }
        Authentication authentication = new Authentication(account.accountId(), CODE, FACTOR_KINDS, authenticatedAt);
        return new VerifiedAuthentication(
                account,
                authentication,
                Long.toString(account.accountId()),
                credential.version(),
                null);
    }

    @Override
    public void verifyCurrent(@NotNull Account account, @NotNull VerifiedAuthentication authentication) {
        TotpCredential credential = credentials.findByAccountId(account.accountId());
        // 验证结果只引用 Account 与版本；凭据替换或删除会使尚未消费的认证尝试立即失效。
        if (credential == null
                || !Long.toString(account.accountId()).equals(authentication.reference())
                || !credential.version().equals(authentication.version())) {
            throw new IllegalStateException("TOTP 凭据已变化");
        }
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
        enrollments.deleteByAccount(accountId);
        credentials.erase(accountId);
        LOG.debug("TOTP Credential 与 enrollment 已擦除 | accountId={}", accountId);
    }

    /**
     * 返回凭据 Secret 加解密使用的稳定 purpose。
     */
    @ApiStatus.Internal
    public static String credentialPurpose(long accountId) {
        return "totp.credential." + accountId + ".secret";
    }
}
