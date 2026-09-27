package com.sca4d.iam.base.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.iam.base.PlatformAccountDirectoryRepository;
import com.sca4d.iam.base.PlatformIdentityService;
import com.sca4d.iam.base.PlatformPrincipal;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iam.rbac.PermissionSelections;
import com.sca4d.iam.rbac.RbacService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 验证 Platform RBAC 写入只锁定目标资源，并在初始化事务提交后释放跨实例锁。 */
class PlatformRbacLockingTest {

    @Test
    @DisplayName("同一角色或主体共用资源锁且不同资源使用不同锁")
    void usesRoleAndPrincipalScopedLocks() {
        List<LockKey> keys = new ArrayList<>();
        DistributedLock distributedLock = executingLock(keys, null);
        PlatformRbacRepository repository = mock(PlatformRbacRepository.class);
        RbacService rbac = mock(RbacService.class);
        PlatformRbacService service = new PlatformRbacService(
                rbac,
                repository,
                mock(PermissionSelections.class),
                mock(IdGenerator.class),
                mock(LocaleCatalog.class),
                mock(IdentityService.class),
                mock(PlatformAccountDirectoryRepository.class),
                distributedLock,
                new TransactionTemplate(transactionManager(null)));

        service.deleteRole(10L);
        service.deleteRole(11L);
        service.revokeAllRoles(20L);
        service.bindDefaultRole(20L);

        assertThat(keys)
                .extracting(LockKey::toString)
                .containsExactly(
                        "LOCK:IAM:RBAC:ROLE:10",
                        "LOCK:IAM:RBAC:ROLE:11",
                        "LOCK:IAM:RBAC:PRINCIPAL:20",
                        "LOCK:IAM:RBAC:PRINCIPAL:20");
    }

    @Test
    @DisplayName("Platform 初始化锁在事务提交后释放")
    void releasesInitializationLockAfterCommit() {
        List<String> events = new ArrayList<>();
        DistributedLock distributedLock = executingLock(new ArrayList<>(), events);
        PlatformIdentityService identities = mock(PlatformIdentityService.class);
        PlatformRbacService rbac = mock(PlatformRbacService.class);
        Account account = new Account(10L, 20L, new RealmKey("platform"));
        when(identities.initializePlatformAccount("admin")).thenAnswer(invocation -> {
            events.add("identity");
            return account;
        });
        when(identities.activePrincipals(account.accountId()))
                .thenReturn(List.of(new PlatformPrincipal(30L)));
        PlatformBootstrapService bootstrap = new PlatformBootstrapService(
                identities,
                rbac,
                distributedLock,
                transactionManager(events));

        assertThat(bootstrap.initialize("admin")).isSameAs(account);

        assertThat(events).containsExactly(
                "lock-start",
                "transaction-start",
                "identity",
                "transaction-commit",
                "lock-end");
    }

    /** 创建直接执行任务并记录锁键与锁边界的测试分布式锁。 */
    private static DistributedLock executingLock(List<LockKey> keys, List<String> events) {
        return new DistributedLock() {
            @Override
            public <T> T execute(LockKey key, Duration waitTime, Supplier<T> task) {
                keys.add(key);
                if (events != null) {
                    events.add("lock-start");
                }
                try {
                    return task.get();
                } finally {
                    if (events != null) {
                        events.add("lock-end");
                    }
                }
            }
        };
    }

    /** 创建记录事务开始、提交和回滚边界的测试事务管理器。 */
    private static PlatformTransactionManager transactionManager(List<String> events) {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenAnswer(invocation -> {
            if (events != null) {
                events.add("transaction-start");
            }
            return new SimpleTransactionStatus();
        });
        doAnswer(invocation -> {
            if (events != null) {
                events.add("transaction-commit");
            }
            return null;
        })
                .when(transactionManager)
                .commit(any(TransactionStatus.class));
        doAnswer(invocation -> {
            if (events != null) {
                events.add("transaction-rollback");
            }
            return null;
        })
                .when(transactionManager)
                .rollback(any(TransactionStatus.class));
        return transactionManager;
    }
}
