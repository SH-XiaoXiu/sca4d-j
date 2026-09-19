package com.sca4d.auth.password;

import com.sca4d.auth.password.credential.PasswordCredential;
import com.sca4d.auth.password.credential.PasswordCredentialRepository;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.AuthenticationMethodCode;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.security.SecretHasher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 创建、替换和验证 Account 的 Password Credential，并在 Account 终态删除时擦除密码摘要。 */
public class PasswordAuthenticationMethod implements AuthenticationMethod<PasswordProof> {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordAuthenticationMethod.class);

    /** Password 方法稳定编码。 */
    public static final AuthenticationMethodCode CODE = new AuthenticationMethodCode("password");

    private final AccountDirectory accounts;

    private final RealmKey accountRealm;

    private final List<AccountIdentifierType> loginIdentifierTypes;

    private final PasswordCredentialRepository credentials;

    private final SecretHasher hasher;

    private final SessionControl sessions;

    private final Clock clock;

    /**
     * 创建 Password 认证方法。
     *
     * @param accounts 账号查找和可用性校验能力
     * @param accountRealm 登录标识使用的认证域
     * @param loginIdentifierTypes Password 登录依次尝试且要求结果无歧义的标识类型
     * @param credentials Password 凭据 Repository
     * @param hasher 密码摘要生成和校验实现
     * @param sessions 替换密码后终止 Account 旧 Session 的控制入口
     * @param clock 认证时间来源
     */
    public PasswordAuthenticationMethod(
            @NotNull AccountDirectory accounts,
            @NotNull RealmKey accountRealm,
            @NotNull List<@NotNull AccountIdentifierType> loginIdentifierTypes,
            @NotNull PasswordCredentialRepository credentials,
            @NotNull SecretHasher hasher,
            @NotNull SessionControl sessions,
            @NotNull Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "账号目录不能为空");
        this.accountRealm = Objects.requireNonNull(accountRealm, "账号认证域不能为空");
        this.loginIdentifierTypes = List.copyOf(
                Objects.requireNonNull(loginIdentifierTypes, "Password 登录标识类型不能为空"));
        if (this.loginIdentifierTypes.isEmpty()) {
            throw new IllegalArgumentException("Password 至少需要一个登录标识类型");
        }
        if (Set.copyOf(this.loginIdentifierTypes).size() != this.loginIdentifierTypes.size()) {
            throw new IllegalArgumentException("Password 登录标识类型不能重复");
        }
        this.credentials = Objects.requireNonNull(credentials, "密码凭据 Repository 不能为空");
        this.hasher = Objects.requireNonNull(hasher, "密码摘要实现不能为空");
        this.sessions = Objects.requireNonNull(sessions, "Session 控制器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Override
    public @NotNull AuthenticationMethodCode code() {
        return CODE;
    }

    @Override
    public @NotNull Class<PasswordProof> proofType() {
        return PasswordProof.class;
    }

    @Override
    public @NotNull Set<AuthenticationFactorKind> factorKinds() {
        return Set.of(AuthenticationFactorKind.KNOWLEDGE);
    }

    @Override
    public boolean canIdentifyAccount() {
        return true;
    }

    @Override
    public boolean available(@NotNull Account account) {
        return credentials.findByAccountId(account.accountId()) != null;
    }

    @Override
    public @NotNull VerifiedAuthentication authenticate(
            @Nullable Account account,
            @NotNull PasswordProof proof) {
        try {
            PasswordProof checkedProof = Objects.requireNonNull(proof, "密码认证材料不能为空");
            String identifier = checkedProof.identifier();
            Account verifiedAccount = account == null ? discoverAccount(identifier) : account;
            if (account != null && identifier != null && !identifier.isBlank()) {
                Account submittedAccount = discoverAccount(identifier);
                if (verifiedAccount.accountId() != submittedAccount.accountId()) {
                    throw new BusinessException(PasswordErrorCode.REJECTED);
                }
            }
            PasswordCredential credential = credentials.findByAccountId(verifiedAccount.accountId());
            String password = Objects.requireNonNull(checkedProof.password(), "密码不能为空");
            if (credential == null
                    || password.isBlank()
                    || !hasher.matches(password, credential.passwordHash())) {
                throw new BusinessException(PasswordErrorCode.REJECTED);
            }
            Authentication authentication = new Authentication(
                    verifiedAccount.accountId(),
                    CODE,
                    factorKinds(),
                    clock.instant());
            return new VerifiedAuthentication(
                    verifiedAccount,
                    authentication,
                    Long.toString(verifiedAccount.accountId()),
                    credential.version(),
                    null);
        } catch (BusinessException exception) {
            throw new BusinessException(PasswordErrorCode.REJECTED, exception);
        }
    }

    @Override
    public void verifyCurrent(@NotNull Account account, @NotNull VerifiedAuthentication authentication) {
        PasswordCredential credential = credentials.findByAccountId(account.accountId());
        if (credential == null
                || !Long.toString(account.accountId()).equals(authentication.reference())
                || !credential.version().equals(authentication.version())) {
            throw new IllegalStateException("Password 凭据已变化");
        }
    }

    /**
     * 仅在目标账号不存在 Password Credential 时写入初始密码摘要。
     *
     * @param username 用于定位 Account 的用户名
     * @param password 待编码的原始密码
     */
    @Transactional
    public void bootstrap(@NotNull String username, @NotNull String password) {
        String checkedPassword = Objects.requireNonNull(password, "初始密码不能为空");
        if (checkedPassword.isBlank()) {
            throw new IllegalStateException("启用密码 bootstrap 时必须提供初始密码");
        }
        Account account = accounts.resolve(
                accountRealm,
                AccountIdentifierType.USERNAME,
                Objects.requireNonNull(username, "Password bootstrap 用户名不能为空"))
                .orElseThrow(() -> new IllegalStateException("找不到 Password bootstrap 对应的 Account"));
        credentials.insertIfAbsent(account.accountId(), hasher.hash(checkedPassword));
        LOG.debug("Password 初始 Credential 已写入事务 | accountId={}", account.accountId());
    }

    /**
     * 为启用的 Account 创建 Password Credential。
     *
     * @param accountId Account 标识
     * @param password 待编码的原始密码
     * @return 新建的 Password Credential；摘要不会离开 Password 模块
     */
    @Transactional
    public @NotNull PasswordCredential create(long accountId, @NotNull String password) {
        String checkedPassword = Objects.requireNonNull(password, "密码不能为空");
        if (checkedPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        Account account = accounts.activeById(accountId);
        PasswordCredential created = credentials.insert(account.accountId(), hasher.hash(checkedPassword));
        LOG.debug("Password Credential 创建已写入事务 | accountId={}", accountId);
        return created;
    }

    /**
     * 读取 Account 当前的 Password Credential。
     *
     * @param accountId Account 标识
     * @return 当前 Password Credential
     * @throws BusinessException Account 尚未设置密码时抛出
     */
    @Transactional(readOnly = true)
    public @NotNull PasswordCredential loadByAccountId(long accountId) {
        return java.util.Optional.ofNullable(credentials.findByAccountId(accountId))
                .orElseThrow(() -> new BusinessException(PasswordErrorCode.CREDENTIAL_NOT_FOUND));
    }

    /**
     * 替换 Password Credential，并终止该 Account 的全部旧 Session。
     *
     * @param accountId Account 标识
     * @param password 待编码的新原始密码
     * @return 替换后的 Password Credential
     */
    @Transactional
    public @NotNull PasswordCredential replace(long accountId, @NotNull String password) {
        String checkedPassword = Objects.requireNonNull(password, "密码不能为空");
        if (checkedPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        PasswordCredential current = loadByAccountId(accountId);
        PasswordCredential updated = credentials.replace(
                accountId,
                hasher.hash(checkedPassword),
                current.version());
        sessions.terminateAccount(updated.accountId(), SessionTerminationReason.AUTHENTICATION_CHANGED);
        LOG.debug(
                "Password Credential 替换和旧 Session 终止已写入事务 | accountId={} version={}",
                accountId,
                updated.version().value());
        return updated;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void eraseAccount(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        credentials.erase(accountId);
        LOG.debug("Password Credential 擦除已写入事务 | accountId={}", accountId);
    }

    /** 按全部配置类型解析同一个输入，并拒绝它们命中不同 Account 的歧义结果。 */
    private Account discoverAccount(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new BusinessException(PasswordErrorCode.REJECTED);
        }
        Account discovered = null;
        for (AccountIdentifierType type : loginIdentifierTypes) {
            Account candidate;
            try {
                candidate = accounts.resolve(accountRealm, type, identifier).orElse(null);
            } catch (BusinessException ignored) {
                continue;
            }
            if (candidate == null) {
                continue;
            }
            if (discovered != null && discovered.accountId() != candidate.accountId()) {
                throw new BusinessException(PasswordErrorCode.REJECTED);
            }
            discovered = candidate;
        }
        if (discovered == null) {
            throw new BusinessException(PasswordErrorCode.REJECTED);
        }
        return discovered;
    }
}
