package com.sca4d.iam.base.rbac;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.identity.Account;
import com.sca4d.iam.base.PlatformIdentityService;
import com.sca4d.iam.base.PlatformPrincipal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 初始化内置 Platform 角色、bootstrap 账号及其 ROOT 角色关系。 */
public class PlatformBootstrapService {

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull PlatformIdentityService identities;

    private final @NotNull PlatformRbacService rbac;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull TransactionTemplate transactions;

    /**
     * 创建 Platform 初始化服务。
     *
     * @param identities Platform 身份服务
     * @param rbac Platform RBAC 服务
     * @param distributedLock 多实例 Platform 初始化锁
     * @param transactionManager Platform 初始化事务管理器
     */
    public PlatformBootstrapService(
            @NotNull PlatformIdentityService identities,
            @NotNull PlatformRbacService rbac,
            @NotNull DistributedLock distributedLock,
            @NotNull PlatformTransactionManager transactionManager) {
        this.identities = Objects.requireNonNull(identities, "Platform 身份服务不能为空");
        this.rbac = Objects.requireNonNull(rbac, "Platform RBAC 服务不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "分布式锁不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    /**
     * 在一个事务中创建缺失的内置角色和 bootstrap platform Account，并保护其 ROOT 角色关系。
     *
     * @param username bootstrap 平台用户名
     * @return 已存在或新建的 platform Account
     * @throws IllegalStateException bootstrap Account 没有恰好关联一个 PlatformPrincipal 时抛出
     */
    @CheckReturnValue
    public @NotNull Account initialize(@NotNull String username) {
        return distributedLock.execute(
                PlatformRbacService.initializationLock(),
                LOCK_WAIT,
                () -> Objects.requireNonNull(
                        transactions.execute(status -> {
                            // 确保内置角色和 bootstrap Account 存在，再读取它唯一的 PlatformPrincipal。
                            Account account = identities.initializePlatformAccount(username);
                            rbac.initializeBuiltInRoles();
                            List<PlatformPrincipal> principals = identities
                                    .activePrincipals(account.accountId());
                            if (principals.size() != 1) {
                                throw new IllegalStateException(
                                        "bootstrap PlatformAccount 必须恰好关联一个 PlatformPrincipal");
                            }

                            // 在同一事务中保护 ROOT 关系，事务提交后才释放跨实例初始化锁。
                            rbac.bindBootstrapRoot(principals.getFirst().principalId());
                            return account;
                        }),
                        "Platform 初始化事务没有返回 Account"));
    }
}
