package com.sca4d.authentication.service;

import com.sca4d.authentication.SessionService;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.authentication.realtime.AuthenticationRealtimePublisher;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Session;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.permission.repository.SessionRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Account 登录 Session 的 IAM 一次装载与集群共享会话刷新实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultSessionService implements SessionService {

    private static final Object PENDING_REFRESH_RESOURCE = new Object();

    private final IamRepository iamRepository;
    private final Authentication authentication;
    private final AuthenticationRealtimePublisher realtimePublisher;

    @Override
    public @NotNull Session create(
            @NotNull Long accountId,
            @Nullable String deviceId,
            @Nullable String terminalName) {
        LoadedSession loaded = load(List.of(accountId)).get(accountId);
        if (loaded == null) {
            throw new UnauthenticatedException(AuthenticationErrorCode.ACCOUNT_SESSION_UNAVAILABLE);
        }
        Session session = authentication.createSession(
                loaded.subject, loaded.account, loaded.principals, loaded.permissions, deviceId, terminalName);
        log.debug(
                "登录会话创建完成 | accountId={} subjectId={} principalCount={}",
                accountId,
                loaded.subject.getSubjectId(),
                loaded.principals.size());
        return session;
    }

    @Override
    public void refreshAfterCommit(@NotNull Collection<Long> subjectIds) {
        List<Long> accountIds = iamRepository.findAccountsBySubjectIds(new LinkedHashSet<>(subjectIds)).stream()
                .map(IamAccountEntity::getId)
                .toList();
        if (accountIds.isEmpty()) {
            return;
        }
        refreshAccountsAfterCommit(accountIds);
    }

    /**
     * 在当前事务提交后刷新指定 Account Session；同一事务重复登记时合并 Account 主键。
     *
     * @param accountIds 受当前业务修改影响的 Account 主键
     */
    private void refreshAccountsAfterCommit(@NotNull Collection<Long> accountIds) {
        List<Long> distinctAccountIds = List.copyOf(new LinkedHashSet<>(accountIds));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refreshSessions(distinctAccountIds);
            return;
        }
        PendingRefresh pending = (PendingRefresh) TransactionSynchronizationManager.getResource(PENDING_REFRESH_RESOURCE);
        if (pending != null) {
            pending.add(distinctAccountIds);
            return;
        }
        PendingRefresh registered = new PendingRefresh(distinctAccountIds);
        TransactionSynchronizationManager.bindResource(PENDING_REFRESH_RESOURCE, registered);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refreshSessions(registered.accountIds());
            }

            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(PENDING_REFRESH_RESOURCE);
            }
        });
    }

    @Override
    public void refreshOnlineAfterCommit() {
        refreshAccountsAfterCommit(authentication.onlineAccountIds());
    }

    /**
     * 用一次 IAM 查询重装全部目标 Account，并替换各自的 Sa-Token Account-Session。
     *
     * <p>Subject 或 Account 已停用、删除时查询不会返回状态，此时结束该 Account 的全部登录会话。Redis 刷新失败发生在业务事务提交
     * 之后，只记录错误并由运维处理，不能把已提交的业务操作返回成失败。
     *
     * @param accountIds 去重且顺序固定的待刷新 Account 主键
     */
    private void refreshSessions(@NotNull List<Long> accountIds) {
        long startedAt = System.nanoTime();
        log.info("登录会话刷新批次开始 | accountCount={}", accountIds.size());
        Map<Long, LoadedSession> sessions;
        try {
            sessions = load(accountIds);
        } catch (RuntimeException exception) {
            log.error(
                    "登录会话刷新批次装载失败，当前批次未更新 | accountCount={}",
                    accountIds.size(),
                    exception);
            return;
        }
        int refreshedCount = 0;
        int endedCount = 0;
        int failedCount = 0;
        int notificationFailedCount = 0;
        for (Long accountId : accountIds) {
            try {
                LoadedSession session = sessions.get(accountId);
                if (session == null) {
                    authentication.endSessions(accountId);
                    endedCount++;
                } else {
                    authentication.replaceSession(
                            accountId, session.subject, session.account, session.principals, session.permissions);
                    refreshedCount++;
                }
            } catch (RuntimeException exception) {
                // 单个 Subject 的 Redis 故障不能阻断同批次其他会话刷新。
                failedCount++;
                log.error(
                        "登录会话刷新失败，继续处理当前批次 | accountId={}",
                        accountId,
                        exception);
                continue;
            }
            try {
                LoadedSession session = sessions.get(accountId);
                if (session != null) {
                    realtimePublisher.sessionChanged(session.subject.getSubjectId());
                }
            } catch (RuntimeException exception) {
                // 实时通知允许丢失；客户端重连后会重新读取 /me，发布故障不能改变已经更新的 Redis Session。
                notificationFailedCount++;
                log.error(
                        "登录会话已刷新，但实时通知发送失败 | accountId={}",
                        accountId,
                        exception);
            }
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "登录会话刷新批次完成 | refreshedCount={} endedCount={} failedCount={} notificationFailedCount={} durationMs={}",
                refreshedCount,
                endedCount,
                failedCount,
                notificationFailedCount,
                durationMs);
    }

    /**
     * 用一次 IAM JOIN 查询聚合多个 Account 的 Session 内容。
     *
     * <p>该私有结果只存在于创建或刷新调用栈，不形成第二个公开会话对象。返回内容随后直接交给 Authentication
     * 写入各自的 Sa-Token Account-Session。
     *
     * @param accountIds 去重后的目标 Account 主键
     * @return 仍可登录的 Account 对应 Session 内容
     */
    private @NotNull Map<Long, LoadedSession> load(@NotNull List<Long> accountIds) {
        Map<Long, Subject> subjects = new LinkedHashMap<>();
        Map<Long, Account> accounts = new LinkedHashMap<>();
        Map<Long, LinkedHashMap<Long, Principal>> principalsByAccount = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<String>> rolesByPrincipal = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<String>> permissionsByPrincipal = new LinkedHashMap<>();
        for (SessionRow row : iamRepository.findSessionRows(accountIds)) {
            Long accountId = Objects.requireNonNull(row.getAccountId(), "会话行缺少 Account 主键");
            Long subjectId = Objects.requireNonNull(row.getSubjectId(), "会话行缺少 Subject 主键");
            AccountScopeType scopeType = Objects.requireNonNull(
                    row.getAccountScopeType(), "会话行缺少 Account 作用域类型");
            Long principalId = Objects.requireNonNull(row.getPrincipalId(), "会话行缺少 Principal 主键");
            PrincipalType principalType = Objects.requireNonNull(
                    row.getPrincipalType(), "会话行缺少 Principal 类型");
            subjects.putIfAbsent(accountId, new Subject(subjectId, row.getSubjectDisplayName()));
            accounts.putIfAbsent(
                    accountId,
                    new Account(
                            accountId,
                            subjectId,
                            scopeType,
                            row.getAccountScopeId(),
                            row.isSecondFactorRequired()));
            principalsByAccount
                    .computeIfAbsent(accountId, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(
                            principalId,
                            Principal.withDisplayName(
                                    principalId,
                                    principalType,
                                    subjectId,
                                    row.getTenantId(),
                                    row.getPrincipalDisplayName()));
            rolesByPrincipal.computeIfAbsent(principalId, ignored -> new LinkedHashSet<>());
            permissionsByPrincipal.computeIfAbsent(principalId, ignored -> new LinkedHashSet<>());
            if (row.getRoleCode() != null) {
                rolesByPrincipal.get(principalId).add(row.getRoleCode());
            }
            if (row.getPermissionCode() != null) {
                permissionsByPrincipal.get(principalId).add(row.getPermissionCode());
            }
        }

        Map<Long, LoadedSession> sessions = new LinkedHashMap<>();
        for (Map.Entry<Long, Subject> subjectEntry : subjects.entrySet()) {
            Long accountId = subjectEntry.getKey();
            List<Principal> principals = List.copyOf(principalsByAccount.get(accountId).values());
            Map<Long, SessionPermission> permissions = new LinkedHashMap<>();
            for (Principal principal : principals) {
                Long principalId = principal.requirePrincipalId();
                permissions.put(
                        principalId,
                        new SessionPermission(
                                principalId,
                                rolesByPrincipal.get(principalId),
                                permissionsByPrincipal.get(principalId)));
            }
            sessions.put(
                    accountId,
                    new LoadedSession(
                            subjectEntry.getValue(),
                            accounts.get(accountId),
                            principals,
                            Map.copyOf(permissions)));
        }
        return Map.copyOf(sessions);
    }

    /** 创建或刷新调用栈内部使用的 Session 内容。 */
    private static final class LoadedSession {

        private final Subject subject;
        private final Account account;
        private final List<Principal> principals;
        private final Map<Long, SessionPermission> permissions;

        /**
         * 固化一次 IAM 聚合查询形成的完整会话内容。
         *
         * @param subject 当前仍可参与登录的 Subject
         * @param account 当前仍可参与登录的 Account
         * @param principals 当前 Subject 的全部可选择 Principal
         * @param permissions 按 Principal 主键索引的角色权限快照
         */
        private LoadedSession(
                @NotNull Subject subject,
                @NotNull Account account,
                @NotNull List<Principal> principals,
                @NotNull Map<Long, SessionPermission> permissions) {
            this.subject = subject;
            this.account = account;
            this.principals = principals;
            this.permissions = permissions;
        }
    }

    /** 同一事务内待刷新的 Subject 去重集合。 */
    private static final class PendingRefresh {

        private final LinkedHashSet<Long> accountIds = new LinkedHashSet<>();

        /**
         * 创建事务内首次登记的待刷新 Subject 集合。
         *
         * @param accountIds 当前业务动作影响的 Account 主键
         */
        private PendingRefresh(@NotNull Collection<Long> accountIds) {
            add(accountIds);
        }

        /**
         * 合并同一事务后续登记的 Subject。
         *
         * @param values 当前业务动作影响的 Subject 主键
         */
        private void add(@NotNull Collection<Long> values) {
            accountIds.addAll(values);
        }

        /**
         * 固化事务提交时需要刷新的 Account 顺序。
         *
         * @return 去重且保持首次登记顺序的 Account 主键
         */
        private @NotNull List<Long> accountIds() {
            return List.copyOf(accountIds);
        }
    }
}
