package com.sca4d.app.server.service;

import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.app.server.platform.PlatformAccountRecord;
import com.sca4d.app.server.platform.PlatformEmailAccountIdentifierNormalizer;
import com.sca4d.app.server.platform.PlatformPhoneAccountIdentifierNormalizer;
import com.sca4d.auth.email.EmailCodeAuthenticationMethod;
import com.sca4d.auth.engine.AuthenticationMethods;
import com.sca4d.auth.password.PasswordAuthenticationMethod;
import com.sca4d.auth.password.credential.PasswordCredential;
import com.sca4d.auth.sms.SmsCodeAuthenticationMethod;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.query.Page;
import com.sca4d.iam.base.PlatformAccountDeletion;
import com.sca4d.iam.base.PlatformAccountDirectory;
import com.sca4d.iam.base.PlatformAccountEntry;
import com.sca4d.iam.base.PlatformAccountLifecycleService;
import com.sca4d.iam.base.PlatformAccountQuery;
import com.sca4d.iam.base.PlatformIdentityService;
import com.sca4d.iam.base.PlatformPrincipal;
import com.sca4d.iam.base.rbac.PlatformRbacService;
import com.sca4d.iam.identity.AccountIdentifier;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iam.identity.IdentityStatus;
import com.sca4d.iam.rbac.PrincipalRoles;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 编排示例 App 的 Platform Account 创建、资料、状态和角色操作。 */
@Service
public class PlatformAccountApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformAccountApplicationService.class);

    private static final Duration ACCOUNT_WRITE_LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull PlatformIdentityService identities;

    private final @NotNull IdentityService identityFacts;

    private final @NotNull PlatformAccountDirectory accounts;

    private final @NotNull PlatformAccountLifecycleService accountLifecycle;

    private final @NotNull PlatformRbacService rbac;

    private final @NotNull AuthenticationMethods authenticationMethods;

    private final @NotNull PasswordAuthenticationMethod passwords;

    private final @NotNull PlatformAuthenticationPolicyService authenticationPolicies;

    private final @NotNull PlatformUserProfileService profiles;

    private final @NotNull PlatformEmailAccountIdentifierNormalizer emailNormalizer;

    private final @NotNull PlatformPhoneAccountIdentifierNormalizer phoneNormalizer;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull TransactionTemplate transactions;

    /**
     * 创建 Platform Account 应用服务。
     *
     * @param identities Platform 身份服务
     * @param identityFacts IAM 身份事实服务
     * @param accounts Platform Account 目录
     * @param accountLifecycle Platform Account 生命周期服务
     * @param rbac Platform RBAC 服务
     * @param authenticationMethods Sca4d Server 安装的认证方法
     * @param authenticationPolicies 示例应用认证策略服务
     * @param profiles App 用户资料服务
     * @param emailNormalizer Platform Email 登录标识规范化器
     * @param distributedLock 多实例账号操作锁
     * @param transactionManager 应用事务管理器
     */
    public PlatformAccountApplicationService(
            @NotNull PlatformIdentityService identities,
            @NotNull IdentityService identityFacts,
            @NotNull PlatformAccountDirectory accounts,
            @NotNull PlatformAccountLifecycleService accountLifecycle,
            @NotNull PlatformRbacService rbac,
            @NotNull AuthenticationMethods authenticationMethods,
            @NotNull PlatformAuthenticationPolicyService authenticationPolicies,
            @NotNull PlatformUserProfileService profiles,
            @NotNull PlatformEmailAccountIdentifierNormalizer emailNormalizer,
            @NotNull PlatformPhoneAccountIdentifierNormalizer phoneNormalizer,
            @NotNull DistributedLock distributedLock,
            @NotNull PlatformTransactionManager transactionManager) {
        this.identities = Objects.requireNonNull(identities, "Platform 身份服务不能为空");
        this.identityFacts = Objects.requireNonNull(identityFacts, "IAM 身份事实服务不能为空");
        this.accounts = Objects.requireNonNull(accounts, "Platform Account 目录不能为空");
        this.accountLifecycle = Objects.requireNonNull(accountLifecycle, "Platform Account 生命周期服务不能为空");
        this.rbac = Objects.requireNonNull(rbac, "Platform RBAC 服务不能为空");
        this.authenticationMethods = Objects.requireNonNull(authenticationMethods, "认证方法目录不能为空");
        this.passwords = this.authenticationMethods.method(PasswordAuthenticationMethod.class);
        this.authenticationPolicies = Objects.requireNonNull(
                authenticationPolicies,
                "应用认证策略服务不能为空");
        this.profiles = Objects.requireNonNull(profiles, "Platform 用户资料服务不能为空");
        this.emailNormalizer = Objects.requireNonNull(emailNormalizer, "Platform Email 规范化器不能为空");
        this.phoneNormalizer = Objects.requireNonNull(phoneNormalizer, "Platform PHONE 规范化器不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "分布式锁不能为空");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    /**
     * 原子创建 Subject、Account、Principal、用户名、初始密码、DEFAULT 角色和 App 资料。
     *
     * @param username 登录用户名
     * @param initialPassword 初始原始密码
     * @param displayName 展示名称
     * @param email 可选邮箱
     * @param phoneNumber 可选规范 E.164 手机号
     * @return 新建的完整 Platform Account
     */
    @Transactional
    @CheckReturnValue
    public @NotNull PlatformAccountRecord create(
            @NotNull String username,
            @NotNull String initialPassword,
            @NotNull String displayName,
            @Nullable String email,
            @Nullable String phoneNumber) {
        PlatformPrincipal principal = identities.provision(username);
        List<Account> linkedAccounts = identities.findActiveAccounts(principal.principalId());
        if (linkedAccounts.size() != 1) {
            throw new IllegalStateException("新建 PlatformPrincipal 必须恰好关联一个 Platform Account");
        }
        Account account = linkedAccounts.getFirst();
        String normalizedEmail = normalizeEmail(email);
        rbac.bindDefaultRole(principal.principalId());
        PasswordCredential passwordCredential = passwords.create(account.accountId(), initialPassword);
        synchronizeEmailIdentifier(account, normalizedEmail);
        PlatformUserProfile profile = profiles.create(
                principal.principalId(),
                displayName,
                normalizedEmail,
                phoneNumber);
        PlatformAccountRecord created = new PlatformAccountRecord(accounts.loadById(account.accountId()), profile);
        LOG.info(
                "Platform Account 应用数据已创建 | accountId={} principalId={}",
                passwordCredential.accountId(),
                principal.principalId());
        return created;
    }

    /**
     * 原子替换 Platform Profile，并让联系邮箱与 Account 的 `EMAIL` 登录标识保持一致。
     *
     * @param principalId Platform Principal 标识
     * @param displayName 新展示名称
     * @param email 新邮箱；空白或 {@code null} 表示清除
     * @param phoneNumber 新的可选规范 E.164 手机号
     * @return 修改后的用户资料
     */
    @Transactional
    @CheckReturnValue
    public @NotNull PlatformUserProfile replaceProfile(
            @NotNull Long principalId,
            @NotNull String displayName,
            @Nullable String email,
            @Nullable String phoneNumber) {
        Account account = activeAccount(principalId);
        String normalizedEmail = normalizeEmail(email);
        synchronizeEmailIdentifier(account, normalizedEmail);
        return profiles.replace(principalId, displayName, normalizedEmail, phoneNumber);
    }

    /**
     * 为已有 Profile 补齐或复核 `EMAIL` 登录标识；重复邮箱会使启动事务失败。
     *
     */
    @Transactional
    public void synchronizeProfileEmailIdentifiers() {
        List<PlatformUserProfile> emailProfiles = profiles.findAllWithEmail();
        int synchronizedCount = 0;
        for (PlatformUserProfile profile : emailProfiles) {
            List<Account> linkedAccounts = identities.findActiveAccounts(profile.principalId());
            if (linkedAccounts.isEmpty()) {
                LOG.debug(
                        "停用 Platform Account 的 Profile Email 标识暂不复核 | principalId={}",
                        profile.principalId());
                continue;
            }
            if (linkedAccounts.size() != 1) {
                throw new IllegalStateException("Platform Profile 必须恰好关联一个启用的 Platform Account");
            }
            Account account = linkedAccounts.getFirst();
            synchronizeEmailIdentifier(account, normalizeEmail(profile.email()));
            synchronizedCount++;
        }
        LOG.info(
                "Platform Profile Email 登录标识已复核 | profileCount={} synchronizedCount={}",
                emailProfiles.size(),
                synchronizedCount);
    }

    /** 在 SMS Code 动作认证完成后把当前 Profile 手机号写成唯一启用 PHONE Identifier。 */
    @Transactional
    public void verifyProfilePhone(@NotNull Long principalId) {
        Account account = activeAccount(principalId);
        String phone = profiles.loadByPrincipalId(principalId).phoneNumber();
        if (phone == null) {
            throw new IllegalStateException("Platform Profile 没有可验证手机号");
        }
        synchronizePhoneIdentifier(account, phoneNormalizer.normalize(phone));
        LOG.info(
                "Platform Profile 手机号已验证并绑定登录标识 | accountId={} principalId={}",
                account.accountId(),
                principalId);
    }

    /**
     * 查询一页 Platform Account 并组合 App 资料。
     *
     * @param query 分页和筛选条件
     * @return 当前页完整账号
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Page<PlatformAccountRecord> find(@NotNull PlatformAccountQuery query) {
        return completeAccounts(accounts.find(query));
    }

    /**
     * 查询指定 Platform 角色已分配或未分配的一页 Account，并组合 App 资料。
     *
     * @param roleId Platform 角色标识
     * @param assigned 查询已分配 Account 时为 {@code true}
     * @param query 分页、用户名和状态筛选
     * @return 当前页完整账号
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Page<PlatformAccountRecord> findRoleAccounts(
            @NotNull Long roleId,
            boolean assigned,
            @NotNull PlatformAccountQuery query) {
        return completeAccounts(rbac.findRoleAccounts(roleId, assigned, query));
    }

    /**
     * 批量增加或移除一个 Platform 角色的 Account。
     *
     * @param roleId Platform 角色标识
     * @param addAccountIds 需要增加目标角色的 Account 标识
     * @param removeAccountIds 需要移除目标角色的 Account 标识
     */
    public void changeRoleAccounts(
            @NotNull Long roleId,
            @NotNull Collection<@NotNull Long> addAccountIds,
            @NotNull Collection<@NotNull Long> removeAccountIds) {
        Map<Long, PrincipalRoles> result = rbac.changeRoleAccounts(
                roleId,
                addAccountIds,
                removeAccountIds);
        LOG.info(
                "Platform 角色 Account 批量修改已完成 | roleId={} requestedAccountCount={} principalCount={}",
                roleId,
                addAccountIds.size() + removeAccountIds.size(),
                result.size());
    }

    /** 为 IAM Account 分页批量补充示例 App 用户资料。 */
    private @NotNull Page<PlatformAccountRecord> completeAccounts(@NotNull Page<PlatformAccountEntry> page) {
        Page<PlatformAccountEntry> checked = Objects.requireNonNull(page, "Platform Account 分页不能为空");
        Map<Long, PlatformUserProfile> profileByPrincipal = new LinkedHashMap<>();
        profiles.findAll(checked.getItems().stream().map(PlatformAccountEntry::principalId).toList())
                .forEach(profile -> profileByPrincipal.put(profile.principalId(), profile));
        return checked.map(
                account -> new PlatformAccountRecord(
                        account,
                        Objects.requireNonNull(
                                profileByPrincipal.get(account.principalId()),
                                "Platform Account 缺少 App 用户资料")));
    }

    /**
     * 读取一个完整 Platform Account。
     *
     * @param accountId Account 标识
     * @return IAM 账号与 App 资料
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull PlatformAccountRecord loadByAccountId(@NotNull Long accountId) {
        PlatformAccountEntry account = accounts.loadById(accountId);
        return new PlatformAccountRecord(account, profiles.loadByPrincipalId(account.principalId()));
    }

    /**
     * 修改 Platform Account 状态并组合 App 资料。
     *
     * @param accountId 目标 Account 标识
     * @param status 新状态
     * @param currentAccountId 当前 Session Account 标识
     * @return 修改后的完整账号
     */
    @Transactional
    @CheckReturnValue
    public @NotNull PlatformAccountRecord changeStatus(
            @NotNull Long accountId,
            @NotNull IdentityStatus status,
            @NotNull Long currentAccountId) {
        PlatformAccountEntry updated = accounts.changeStatus(accountId, status, currentAccountId);
        return new PlatformAccountRecord(updated, profiles.loadByPrincipalId(updated.principalId()));
    }

    /**
     * 读取 Account 唯一 PlatformPrincipal 的角色。
     *
     * @param accountId Account 标识
     * @return 当前角色及角色集合版本
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull PrincipalRoles findRoles(@NotNull Long accountId) {
        return rbac.findPrincipalRoles(accounts.loadById(accountId).principalId());
    }

    /**
     * 原子替换 Account 唯一 PlatformPrincipal 的角色。
     *
     * @param accountId Account 标识
     * @param roleIds 新角色标识集合
     * @return 替换后的角色
     */
    @CheckReturnValue
    public @NotNull PrincipalRoles replaceRoles(
            @NotNull Long accountId,
            @NotNull Collection<@NotNull Long> roleIds) {
        Long checkedAccountId = Objects.requireNonNull(accountId, "Account 标识不能为空");
        return distributedLock.execute(
                accountWriteLock(checkedAccountId),
                ACCOUNT_WRITE_LOCK_WAIT,
                () -> rbac.replacePrincipalRoles(
                        accounts.loadById(checkedAccountId).principalId(),
                        roleIds));
    }

    /**
     * 为目标 Platform Account 替换头像。
     *
     * @param accountId 目标 Account 标识
     * @param uploader 当前上传并绑定头像的 Platform Principal
     * @param fileId 已完成上传的头像文件标识
     * @return 修改后的完整账号
     */
    @CheckReturnValue
    public @NotNull PlatformAccountRecord replaceAvatar(
            @NotNull Long accountId,
            @NotNull PlatformPrincipal uploader,
            long fileId) {
        PlatformAccountEntry account = accounts.loadById(accountId);
        PlatformUserProfile profile = profiles.replaceAvatar(
                account.principalId(),
                uploader,
                fileId);
        return new PlatformAccountRecord(account, profile);
    }

    /**
     * 删除目标 Platform Account 的头像。
     *
     * @param accountId 目标 Account 标识
     * @return 修改后的完整账号
     */
    @CheckReturnValue
    public @NotNull PlatformAccountRecord deleteAvatar(@NotNull Long accountId) {
        PlatformAccountEntry account = accounts.loadById(accountId);
        PlatformUserProfile profile = profiles.deleteAvatar(account.principalId());
        return new PlatformAccountRecord(account, profile);
    }

    /**
     * 删除 Platform Account，并同步清理示例 App 安装的认证数据和个人资料。
     *
     * @param accountId 目标 Account 标识
     * @param currentAccountId 当前 Session Account 标识
     */
    public void delete(
            @NotNull Long accountId,
            @NotNull Long currentAccountId) {
        Long checkedAccountId = Objects.requireNonNull(accountId, "Account 标识不能为空");
        Long checkedCurrentAccountId = Objects.requireNonNull(currentAccountId, "当前 Account 标识不能为空");
        PlatformAccountDeletion deletion = distributedLock.execute(
                accountWriteLock(checkedAccountId),
                ACCOUNT_WRITE_LOCK_WAIT,
                () -> {
                    PlatformAccountEntry current = accounts.loadById(checkedAccountId);
                    return distributedLock.execute(
                            PlatformUserProfileService.profileLock(current.principalId()),
                            ACCOUNT_WRITE_LOCK_WAIT,
                            () -> Objects.requireNonNull(transactions.execute(status -> {
                                PlatformAccountDeletion result = accountLifecycle.delete(
                                        checkedAccountId,
                                        checkedCurrentAccountId);
                                authenticationMethods.eraseAccount(result.accountId());
                                authenticationPolicies.erase(result.accountId());
                                if (result.principalDeleted()) {
                                    profiles.deleteWithinTransaction(result.principalId());
                                }
                                return result;
                            }), "账号删除事务没有返回结果"));
                });
        LOG.info(
                "Platform Account 应用数据已删除 | accountId={} principalId={} profileDeleted={}",
                deletion.accountId(),
                deletion.principalId(),
                deletion.principalDeleted());
    }

    /** 返回同一 Account 角色替换和终态删除共用的分布式锁键。 */
    private static @NotNull LockKey accountWriteLock(@NotNull Long accountId) {
        return new LockKey("platform", "account", accountId.toString());
    }

    /** 返回 Principal 唯一且启用的 Platform Account。 */
    private @NotNull Account activeAccount(@NotNull Long principalId) {
        List<Account> linkedAccounts = identities.findActiveAccounts(
                Objects.requireNonNull(principalId, "Platform Principal 标识不能为空"));
        if (linkedAccounts.size() != 1) {
            throw new IllegalStateException("Platform Profile 必须恰好关联一个启用的 Platform Account");
        }
        return linkedAccounts.getFirst();
    }

    /** 把可选 Profile 邮箱转换成 `EMAIL` 标识与通知共同使用的稳定地址。 */
    private @Nullable String normalizeEmail(@Nullable String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return emailNormalizer.normalize(email);
    }

    /** 在当前事务内增改删 Account 唯一的 `EMAIL` 登录标识。 */
    private void synchronizeEmailIdentifier(@NotNull Account account, @Nullable String email) {
        List<AccountIdentifier> identifiers = identityFacts.findIdentifiers(account.accountId())
                .stream()
                .filter(identifier -> EmailCodeAuthenticationMethod.EMAIL_IDENTIFIER_TYPE.equals(identifier.type()))
                .toList();
        if (identifiers.size() > 1) {
            throw new IllegalStateException("Platform Account 存在多个 EMAIL 登录标识");
        }
        AccountIdentifier current = identifiers.isEmpty() ? null : identifiers.getFirst();
        if (email == null) {
            if (current != null) {
                identityFacts.removeIdentifier(current.id());
            }
            return;
        }
        if (current == null) {
            AccountIdentifier added = identityFacts.addIdentifier(
                    account.accountId(),
                    EmailCodeAuthenticationMethod.EMAIL_IDENTIFIER_TYPE,
                    email);
            LOG.debug(
                    "Platform Account EMAIL 标识已新增 | accountId={} identifierId={}",
                    account.accountId(),
                    added.id());
            return;
        }
        AccountIdentifier updated = current;
        if (!current.value().equals(email) || !current.normalizedValue().equals(email)) {
            updated = identityFacts.changeIdentifierValue(current.id(), email);
        }
        if (updated.status() != IdentityStatus.ACTIVE) {
            AccountIdentifier activated = identityFacts.changeIdentifierStatus(updated.id(), IdentityStatus.ACTIVE);
            LOG.debug(
                    "Platform Account EMAIL 标识已启用 | accountId={} identifierId={}",
                    account.accountId(),
                    activated.id());
        }
    }

    /** 在当前事务内增改并启用 Account 唯一的 PHONE 登录标识。 */
    private void synchronizePhoneIdentifier(@NotNull Account account, @NotNull String phoneNumber) {
        List<AccountIdentifier> identifiers = identityFacts.findIdentifiers(account.accountId())
                .stream()
                .filter(identifier -> SmsCodeAuthenticationMethod.PHONE_IDENTIFIER_TYPE.equals(identifier.type()))
                .toList();
        if (identifiers.size() > 1) {
            throw new IllegalStateException("Platform Account 存在多个 PHONE 登录标识");
        }
        AccountIdentifier current = identifiers.isEmpty() ? null : identifiers.getFirst();
        if (current == null) {
            identityFacts.addIdentifier(
                    account.accountId(),
                    SmsCodeAuthenticationMethod.PHONE_IDENTIFIER_TYPE,
                    phoneNumber);
            return;
        }
        AccountIdentifier updated = current.value().equals(phoneNumber)
                ? current
                : identityFacts.changeIdentifierValue(current.id(), phoneNumber);
        if (updated.status() != IdentityStatus.ACTIVE) {
            identityFacts.changeIdentifierStatus(updated.id(), IdentityStatus.ACTIVE);
        }
    }
}
