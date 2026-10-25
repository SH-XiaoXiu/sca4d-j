package com.sca4d.iam.base;

import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.spring.transaction.TransactionCallbacks;
import com.sca4d.iam.acl.AclService;
import com.sca4d.iam.base.acl.PlatformAclRepository;
import com.sca4d.iam.base.rbac.PlatformRbacService;
import com.sca4d.iam.identity.IamErrorCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 校验并删除 Platform Account，维护共享 Subject、Principal、授权关系和 Account Session。 */
public class PlatformAccountLifecycleService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformAccountLifecycleService.class);

    private final @NotNull AclService acl;

    private final @NotNull PlatformRbacService rbac;

    private final @NotNull PlatformAccountLifecycleRepository repository;

    private final @Nullable SessionControl sessions;

    /** 创建同步执行身份隔离和授权清理的 Platform Account 生命周期服务。 */
    public PlatformAccountLifecycleService(
            @NotNull PlatformAccountLifecycleRepository repository,
            @NotNull PlatformRbacService rbac,
            @NotNull AclService acl,
            @Nullable SessionControl sessions) {
        this.repository = Objects.requireNonNull(repository, "Platform Account 生命周期 Repository 不能为空");
        this.rbac = Objects.requireNonNull(rbac, "Platform RBAC 服务不能为空");
        this.acl = Objects.requireNonNull(acl, "Platform ACL 服务不能为空");
        this.sessions = sessions;
    }

    /**
     * 删除 Platform Account。
     *
     * <p>当前 Account 和受保护 ROOT Account 不能删除。方法先完成全部保护规则校验，再终止现有 Session，
     * 最后在同一事务中逻辑删除身份主记录并物理删除关系数据。只有 Subject 或 Principal 没有其他未删除
     * Account 时才删除对应身份；事务提交后再次终止 Session，以覆盖删除期间完成的登录。
     *
     * @param accountId 目标 Account 标识
     * @param currentAccountId 发起请求的 Account 标识
     * @return IAM 实际删除的身份对象
     * @throws BusinessException 账号不存在、受保护或尝试删除自己时抛出
     */
    @CheckReturnValue
    @Transactional
    public @NotNull PlatformAccountDeletion delete(
            @NotNull Long accountId,
            @NotNull Long currentAccountId) {
        PlatformAccountEntry account = repository.find(accountId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.ACCOUNT_UNAVAILABLE));
        if (account.accountId().equals(Objects.requireNonNull(currentAccountId, "当前 Account 标识不能为空"))) {
            throw new BusinessException(IamErrorCode.CURRENT_ACCOUNT);
        }
        if (account.protectedRoot()) {
            throw new BusinessException(IamErrorCode.PROTECTED_ACCOUNT);
        }

        // 保护规则全部通过后先终止现有 Session，阻止账号在数据库清理期间继续发起在线请求。
        terminate(account.accountId());
        boolean deleteSubject = !repository.hasOtherSubjectAccount(account.subjectId(), account.accountId());
        boolean deletePrincipal = !repository.hasOtherPrincipalAccount(account.principalId(), account.accountId());

        // Account 和登录标识保留审计事实，Account 与 Principal 的关系在删除时物理移除。
        repository.isolateAccount(account.accountId());
        if (deletePrincipal) {
            rbac.revokeAllRoles(account.principalId());
            acl.revokeByPrincipal(PlatformAclRepository.PLATFORM, account.principalId());
            repository.isolatePrincipal(account.principalId());
        }
        if (deleteSubject) {
            repository.isolateSubject(account.subjectId());
        }

        // 事务提交后再次终止 Session，删除失败回滚时不执行，删除期间完成的登录也会被清理。
        PlatformAccountDeletion deletion = new PlatformAccountDeletion(
                account.accountId(),
                account.subjectId(),
                account.principalId(),
                deletePrincipal,
                deleteSubject);
        TransactionCallbacks.afterCommit(() -> completeDeletion(deletion));
        return deletion;
    }

    /** 终止 Account 的全部 Session；未安装 Auth 时不执行外部操作。 */
    private void terminate(@NotNull Long accountId) {
        if (sessions != null) {
            sessions.terminateAccount(accountId, SessionTerminationReason.IDENTITY_UNAVAILABLE);
        }
    }

    /** 再次终止 Session，并记录已经提交的 IAM 删除结果。 */
    private void completeDeletion(@NotNull PlatformAccountDeletion deletion) {
        try {
            terminate(deletion.accountId());
        } catch (RuntimeException exception) {
            // 账号删除已经提交，残留 Session 在下一次鉴权时因身份不可用被拒绝；这里只保留注销失败的运维记录，不回滚删除。
            LOG.error(
                    "Platform Account 删除已提交但二次 Session 注销失败 | accountId={}",
                    deletion.accountId(),
                    exception);
        }
        LOG.info(
                "Platform Account 已删除 | accountId={} principalDeleted={} subjectDeleted={}",
                deletion.accountId(),
                deletion.principalDeleted(),
                deletion.subjectDeleted());
    }
}
