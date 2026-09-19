package com.sca4d.iam.base;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.iam.identity.IamErrorCode;
import com.sca4d.iam.identity.IdentityService;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 组合 IAM 公开操作管理内置 Platform 身份。 */
public class PlatformIdentityService {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformIdentityService.class);

    /** Platform 账号固定使用的认证域。 */
    public static final @NotNull RealmKey PLATFORM_REALM = new RealmKey("platform");

    private final @NotNull IdentityService identities;

    /**
     * 创建 Platform 身份领域服务。
     *
     * @param identities IAM 身份事实服务
     */
    public PlatformIdentityService(@NotNull IdentityService identities) {
        this.identities = Objects.requireNonNull(identities, "身份服务不能为空");
    }

    /**
     * 在一个事务内开通 Platform Subject、Account、登录标识、Principal 和选择关系。
     *
     * @param rawUsername 原始用户名
     * @return 新建 PlatformPrincipal
     */
    @CheckReturnValue
    @Transactional
    public @NotNull PlatformPrincipal provision(@NotNull String rawUsername) {
        Subject subject = identities.createSubject(SubjectType.HUMAN);
        Account account = identities.createAccount(subject.subjectId(), PLATFORM_REALM);
        long identifierId = identities
                .addIdentifier(account.accountId(), AccountIdentifierType.USERNAME, rawUsername)
                .id();
        PlatformPrincipal principal = new PlatformPrincipal(identities.createPrincipal(PlatformPrincipal.class));
        identities.link(account.accountId(), principal.principalId());
        LOG.info(
                "Platform 身份开通完成 | subjectId={} accountId={} identifierId={} principalId={}",
                subject.subjectId(),
                account.accountId(),
                identifierId,
                principal.principalId());
        return principal;
    }

    /**
     * 通过登录标识解析 Platform Account。
     *
     * @param type 登录标识类型
     * @param rawValue 原始登录标识值
     * @return 匹配且完整可用的 platform realm Account
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull Optional<Account> resolvePlatformAccount(
            @NotNull AccountIdentifierType type,
            @NotNull String rawValue) {
        Optional<Account> account = identities.resolveAccount(PLATFORM_REALM, type, rawValue)
                .flatMap(identities::findAccount);
        LOG.debug(
                "Platform Account 解析完成 | identifierType={} matched={}",
                type.value(),
                account.isPresent());
        return account;
    }

    /**
     * 读取必须存在、启用且属于 Platform realm 的 Account。
     *
     * @param accountId Account 标识
     * @return platform realm Account
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull Account activeAccountById(@NotNull Long accountId) {
        Account account = identities.activeAccountById(accountId);
        if (!PLATFORM_REALM.equals(account.realmKey())) {
            throw new BusinessException(IamErrorCode.ACCOUNT_UNAVAILABLE);
        }
        LOG.trace("Platform Account 可用性校验通过 | accountId={}", account.accountId());
        return account;
    }

    /**
     * 读取必须存在且启用的 Platform Subject。
     *
     * @param subjectId Subject 标识
     * @return 启用的 Subject
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull Subject activeSubjectById(@NotNull Long subjectId) {
        return identities.activeSubjectById(subjectId);
    }

    /**
     * 读取 Account 关联且启用的全部 Platform Principal。
     *
     * @param accountId Account 标识
     * @return 至少包含一个元素的不可修改列表
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull @Unmodifiable List<@NotNull PlatformPrincipal> activePrincipals(
            @NotNull Long accountId) {
        List<PlatformPrincipal> principals = identities
                .findActiveLinkedPrincipalIds(accountId, PlatformPrincipal.class)
                .stream()
                .map(PlatformPrincipal::new)
                .toList();
        if (principals.isEmpty()) {
            throw new BusinessException(IamErrorCode.PRINCIPAL_UNAVAILABLE);
        }
        LOG.debug(
                "Platform Account 关联 Principal 读取完成 | accountId={} principalCount={}",
                accountId,
                principals.size());
        return principals;
    }

    /**
     * 读取 Principal 关联且属于 Platform realm 的启用 Account。
     *
     * @param principalId Principal 标识
     * @return 不可修改的 platform realm Account 列表
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull @Unmodifiable List<@NotNull Account> findActiveAccounts(
            @NotNull Long principalId) {
        List<Account> accounts = identities.findActiveLinkedAccounts(principalId)
                .stream()
                .filter(account -> PLATFORM_REALM.equals(account.realmKey()))
                .toList();
        LOG.debug(
                "Platform Principal 关联 Account 读取完成 | principalId={} accountCount={}",
                principalId,
                accounts.size());
        return accounts;
    }

    /**
     * 返回既有初始化账号，或新建一套没有默认角色绑定的 Platform 身份。
     *
     * @param username 原始用户名
     * @return 初始化后的 platform realm Account
     */
    @CheckReturnValue
    @Transactional
    public @NotNull Account initializePlatformAccount(@NotNull String username) {
        Optional<Account> existing = resolvePlatformAccount(AccountIdentifierType.USERNAME, username);
        if (existing.isPresent()) {
            LOG.debug("Platform 初始化复用既有 Account | accountId={}", existing.get().accountId());
            return existing.get();
        }
        PlatformPrincipal principal = provision(username);
        List<Long> accounts = identities.findLinkedAccountIds(principal.principalId());
        if (accounts.size() != 1) {
            throw new IllegalStateException("新建 PlatformPrincipal 必须恰好关联一个 PlatformAccount");
        }
        Account account = activeAccountById(accounts.getFirst());
        LOG.info(
                "Platform 初始化 Account 创建完成 | accountId={} principalId={}",
                account.accountId(),
                principal.principalId());
        return account;
    }
}
