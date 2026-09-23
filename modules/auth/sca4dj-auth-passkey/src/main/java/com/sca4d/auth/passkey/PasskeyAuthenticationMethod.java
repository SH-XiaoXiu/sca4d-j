package com.sca4d.auth.passkey;

import com.sca4d.auth.passkey.ceremony.PasskeyAssertionService;
import com.sca4d.auth.passkey.credential.PasskeyCredential;
import com.sca4d.auth.passkey.credential.PasskeyCredentialRepository;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.exception.Base64UrlException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.Set;

/**
 * 使用 discoverable WebAuthn assertion 验证账号，并且只贡献 POSSESSION 因素。
 */
public class PasskeyAuthenticationMethod implements AuthenticationMethod<PasskeyProof> {

    private static final Logger LOG = LoggerFactory.getLogger(PasskeyAuthenticationMethod.class);

    /**
     * Passkey 方法稳定编码。
     */
    public static final TreeCode CODE = TreeCode.ofSlash("AUTHENTICATION/PASSKEY");

    private static final Set<AuthenticationFactorKind> FACTOR_KINDS = Set.of(AuthenticationFactorKind.POSSESSION);

    private final PasskeyAssertionService assertions;

    private final PasskeyCredentialRepository credentials;

    private final AccountDirectory accounts;

    private final Clock clock;

    /**
     * 创建 Passkey 认证方法。
     */
    public PasskeyAuthenticationMethod(
            @NotNull PasskeyAssertionService assertions,
            @NotNull PasskeyCredentialRepository credentials,
            @NotNull AccountDirectory accounts,
            @NotNull Clock clock) {
        this.assertions = Objects.requireNonNull(assertions, "Passkey assertion 服务不能为空");
        this.credentials = Objects.requireNonNull(credentials, "Passkey Credential Repository 不能为空");
        this.accounts = Objects.requireNonNull(accounts, "AccountDirectory 不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public @NotNull Class<PasskeyProof> proofType() {
        return PasskeyProof.class;
    }

    @Override
    public @NotNull Set<AuthenticationFactorKind> factorKinds() {
        return FACTOR_KINDS;
    }

    @Override
    public boolean canIdentifyAccount() {
        return true;
    }

    @Override
    public boolean available(@NotNull Account account) {
        return !credentials.findAll(account.accountId()).isEmpty();
    }

    @Override
    public @NotNull VerifiedAuthentication authenticate(
            @Nullable Account expectedAccount,
            @NotNull PasskeyProof proof) {
        try {
            PasskeyCredential credential = assertions.finish(expectedAccount, proof);
            Account account = expectedAccount == null
                    ? accounts.activeById(credential.accountId())
                    : expectedAccount;
            Authentication authentication = new Authentication(
                    account.accountId(),
                    CODE,
                    FACTOR_KINDS,
                    clock.instant());
            return new VerifiedAuthentication(
                    account,
                    authentication,
                    new ByteArray(credential.credentialId()).getBase64Url(),
                    credential.version(),
                    null);
        } catch (BusinessException exception) {
            throw new BusinessException(PasskeyErrorCode.ASSERTION_REJECTED, exception);
        }
    }

    @Override
    public void verifyCurrent(@NotNull Account account, @NotNull VerifiedAuthentication authentication) {
        try {
            PasskeyCredential credential = credentials.findByCredentialId(
                    ByteArray.fromBase64Url(authentication.reference()).getBytes());
            if (credential == null
                    || credential.accountId() != account.accountId()
                    || !credential.version().equals(authentication.version())) {
                throw new IllegalStateException("Passkey Credential 已变化");
            }
        } catch (Base64UrlException exception) {
            throw new IllegalStateException("Passkey 验证结果引用无效", exception);
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
        credentials.erase(accountId);
        LOG.debug("Passkey Credential 已擦除 | accountId={}", accountId);
    }
}
