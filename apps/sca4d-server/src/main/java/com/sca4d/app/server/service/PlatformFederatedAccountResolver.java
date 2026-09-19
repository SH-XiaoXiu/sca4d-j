package com.sca4d.app.server.service;

import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.auth.email.EmailCodeAuthenticationMethod;
import com.sca4d.auth.federation.FederatedAccountResolution;
import com.sca4d.auth.federation.FederatedAccountResolver;
import com.sca4d.auth.federation.FederatedErrorCode;
import com.sca4d.auth.federation.FederatedIdentity;
import com.sca4d.auth.federation.FederatedProfile;
import com.sca4d.auth.federation.FederatedProvider;
import com.sca4d.auth.federation.FederatedUnlinkedIdentityAction;
import com.sca4d.auth.federation.identity.FederatedIdentityService;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.iam.base.PlatformIdentityService;
import com.sca4d.iam.base.PlatformPrincipal;
import com.sca4d.iam.base.rbac.PlatformRbacService;
import com.sca4d.iam.identity.AccountIdentifier;
import com.sca4d.iam.identity.IdentityService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 把联邦资料解析为 Sca4d Server Platform Account，并按 Provider 策略执行 JIT。
 */
@Service
public class PlatformFederatedAccountResolver implements FederatedAccountResolver {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformFederatedAccountResolver.class);

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final PlatformIdentityService identities;

    private final IdentityService identityFacts;

    private final PlatformRbacService rbac;

    private final PlatformUserProfileService profiles;

    private final FederatedIdentityService identityBindings;

    private final DistributedLock lock;

    private final TransactionTemplate transactions;

    /**
     * 创建 Platform 联邦账号解析器。
     */
    public PlatformFederatedAccountResolver(
            PlatformIdentityService identities,
            IdentityService identityFacts,
            PlatformRbacService rbac,
            PlatformUserProfileService profiles,
            FederatedIdentityService identityBindings,
            DistributedLock lock,
            PlatformTransactionManager transactionManager) {
        this.identities = Objects.requireNonNull(identities, "Platform 身份服务不能为空");
        this.identityFacts = Objects.requireNonNull(identityFacts, "IAM 身份服务不能为空");
        this.rbac = Objects.requireNonNull(rbac, "Platform RBAC 服务不能为空");
        this.profiles = Objects.requireNonNull(profiles, "Platform Profile 服务不能为空");
        this.identityBindings = Objects.requireNonNull(identityBindings, "联邦身份服务不能为空");
        this.lock = Objects.requireNonNull(lock, "分布式锁不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public @NotNull FederatedAccountResolution resolve(
            @NotNull FederatedProvider provider,
            @NotNull FederatedProfile profile) {
        return lock.execute(
                federatedIdentityLock(provider, profile),
                LOCK_WAIT,
                () -> Objects.requireNonNull(
                        transactions.execute(status -> resolveInTransaction(provider, profile)),
                        "联邦账号解析事务没有返回结果"));
    }

    @Override
    public @NotNull FederatedAccountResolution link(
            @NotNull Account account,
            @NotNull FederatedProvider provider,
            @NotNull FederatedProfile profile) {
        Account input = Objects.requireNonNull(account, "待绑定 Account 不能为空");
        return lock.execute(
                federatedIdentityLock(provider, profile),
                LOCK_WAIT,
                () -> Objects.requireNonNull(
                        transactions.execute(status -> {
                            Account current = loadAccountById(input.accountId());
                            FederatedIdentity identity = identityBindings.link(
                                    provider,
                                    current.accountId(),
                                    profile);
                            return new FederatedAccountResolution(current, identity.id(), identity.version());
                        }),
                        "联邦账号绑定事务没有返回结果"));
    }

    @Override
    @Transactional(readOnly = true)
    public void verifyCurrentBinding(@NotNull Account account, @NotNull FederatedAccountResolution resolution) {
        Account current = identities.activeAccountById(account.accountId());
        if (resolution.account().accountId() != current.accountId() || resolution.identityId() <= 0) {
            throw new BusinessException(FederatedErrorCode.AUTHORIZATION_REJECTED);
        }
        identityBindings
                .verifyCurrentBinding(resolution.identityId(), account.accountId(), resolution.identityVersion());
    }

    /**
     * 在外部身份锁内查找绑定，或按 Provider 策略创建、绑定本地账号。
     */
    private FederatedAccountResolution resolveInTransaction(FederatedProvider provider, FederatedProfile profile) {
        // 已绑定的外部身份只刷新资料，并要求关联 Account 当前仍可用于登录。
        FederatedIdentity linked = identityBindings.find(provider, profile);
        if (linked != null) {
            FederatedIdentity refreshed = identityBindings.refresh(linked, profile);
            return new FederatedAccountResolution(
                    loadAccountById(refreshed.accountId()),
                    refreshed.id(),
                    refreshed.version());
        }

        // Provider 明确允许时，使用已经验证的邮箱复用现有 Account，避免创建重复主体。
        if (provider.autoLinkVerifiedEmail() && profile.emailVerified() && profile.email() != null) {
            Account matched = identities
                    .resolvePlatformAccount(EmailCodeAuthenticationMethod.EMAIL_IDENTIFIER_TYPE, profile.email())
                    .orElse(null);
            if (matched != null) {
                FederatedIdentity identity = identityBindings.link(provider, matched.accountId(), profile);
                return new FederatedAccountResolution(matched, identity.id(), identity.version());
            }
        }

        if (provider.unlinkedIdentityAction() != FederatedUnlinkedIdentityAction.JIT_CREATE) {
            throw new BusinessException(FederatedErrorCode.UNLINKED_IDENTITY);
        }

        // JIT 创建 Principal、Account 和默认角色，任一写入失败时由外层事务整体回滚。
        String username = "federated-" + provider.code() + "-" + subjectKey(profile);
        PlatformPrincipal principal = identities.provision(username);
        List<Account> linkedAccounts = identities.findActiveAccounts(principal.principalId());
        if (linkedAccounts.size() != 1)
            throw new IllegalStateException("JIT 创建的 PlatformPrincipal 必须关联一个 Account");
        Account account = linkedAccounts.getFirst();
        rbac.bindDefaultRole(principal.principalId());
        String email = profile.emailVerified() ? profile.email() : null;
        if (email != null) {
            AccountIdentifier identifier = identityFacts
                    .addIdentifier(account.accountId(), EmailCodeAuthenticationMethod.EMAIL_IDENTIFIER_TYPE, email);
            LOG.debug(
                    "联邦账号邮箱标识已新增 | providerCode={} accountId={} identifierId={}",
                    provider.code(),
                    account.accountId(),
                    identifier.id());
        }

        // 保存应用资料后建立外部身份绑定，使事务提交后的下一次解析能够直接命中。
        PlatformUserProfile createdProfile = profiles.create(
                principal.principalId(),
                profile.displayName() == null ? provider.code() : profile.displayName(),
                email,
                null);
        LOG.debug(
                "联邦账号资料已创建 | providerCode={} principalId={}",
                provider.code(),
                createdProfile.principalId());

        FederatedIdentity identity = identityBindings.link(provider, account.accountId(), profile);
        return new FederatedAccountResolution(account, identity.id(), identity.version());
    }

    /**
     * 要求本地 Account 仍然启用并属于 Platform 认证域。
     */
    private Account loadAccountById(long id) {
        return identities.activeAccountById(id);
    }

    /**
     * 返回同一 Provider 外部身份在全部解析和绑定入口共用的分布式锁键。
     */
    private @NotNull LockKey federatedIdentityLock(
            @NotNull FederatedProvider provider,
            @NotNull FederatedProfile profile) {
        FederatedProvider checkedProvider = Objects.requireNonNull(provider, "联邦 Provider 不能为空");
        FederatedProfile checkedProfile = Objects.requireNonNull(profile, "联邦 Profile 不能为空");
        return new LockKey("federated-identity", checkedProvider.code(), subjectKey(checkedProfile));
    }

    /**
     * 使用 issuer 与 subject 计算不暴露外部身份原文的分布式锁键。
     */
    private String subjectKey(FederatedProfile profile) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (Objects.toString(profile.issuer(), "") + "\u0000" + profile.subject())
                                                    .getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 32);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算外部身份锁键", exception);
        }
    }
}
