package com.sca4d.iam.repository;

import static com.mybatisflex.core.query.QueryMethods.column;
import static com.mybatisflex.core.query.QueryMethods.lower;

import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.ResourceAccess;
import com.sca4d.common.access.ResourceAccessDeniedException;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.dataaccess.PostgresConstraintViolation;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.permission.PermissionCatalog;
import com.sca4d.common.permission.PermissionItem;
import com.sca4d.common.web.ClientStateTarget;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.entity.IamDefaultProfileEntity;
import com.sca4d.entity.IamPermissionEntity;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.entity.IamPrincipalRoleEntity;
import com.sca4d.entity.IamRoleEntity;
import com.sca4d.entity.IamRolePermissionEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.entity.IamTenantEntity;
import com.sca4d.entity.IamTenantMembershipEntity;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.account.ManagedAccount;
import com.sca4d.iam.cleanup.SubjectCleanupTask;
import com.sca4d.iam.cleanup.TenantCleanupTask;
import com.sca4d.iam.client.IamClientStateTargets;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.identifier.AccountIdentifierStatus;
import com.sca4d.iam.identifier.AccountIdentifierType;
import com.sca4d.iam.principal.PlatformPrincipal;
import com.sca4d.iam.principal.PlatformRole;
import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.iam.principal.RoleStatus;
import com.sca4d.iam.resource.AccountReference;
import com.sca4d.iam.resource.CredentialReference;
import com.sca4d.iam.resource.IamResourceTypes;
import com.sca4d.iam.resource.PrincipalReference;
import com.sca4d.iam.resource.RoleReference;
import com.sca4d.iam.resource.SubjectReference;
import com.sca4d.iam.resource.TenantReference;
import com.sca4d.iam.subject.SubjectStatus;
import com.sca4d.iam.tenant.TenantStatus;
import com.sca4d.iam.tenant.TenantType;
import com.sca4d.mapper.IamAccountMapper;
import com.sca4d.mapper.IamMapper;
import com.sca4d.permission.repository.SessionRow;
import com.sca4d.role.ManagedPermission;
import com.sca4d.role.ManagedRole;
import com.sca4d.role.RoleErrorCode;
import com.sca4d.role.repository.RoleUsageCount;
import com.sca4d.tenant.MembershipStatus;
import com.sca4d.tenant.Tenant;
import com.sca4d.tenant.TenantErrorCode;
import com.sca4d.tenant.TenantMembership;
import com.sca4d.tenant.TenantRole;
import com.sca4d.tenant.policy.TenantAccessCriteria;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 读写 Subject、Credential、Principal、角色、权限和租户关系。
 *
 * <p>该仓储集中 Subject、Credential、Principal、Tenant 及其 IAM 关系的存储不变量。DEFAULT Profile 和成员展示
 * 对象由所属 Repository 管理；Service 只决定业务规则、允许的状态流转与副作用。
 */
@Repository
@RequiredArgsConstructor
public class IamRepository {

    private final IamMapper mapper;
    private final IamAccountMapper accountMapper;
    private final IdGenerator idGenerator;
    private final ResourceAccess resourceAccess;

    /**
     * 创建一个处于 ACTIVE 状态的 Subject。
     *
     * @param displayName Subject 展示名称；允许尚未补充
     * @return 已写入数据库并带有集群生成 ID 的 Subject
     */
    public @NotNull IamSubjectEntity createSubject(@Nullable String displayName) {
        IamSubjectEntity stored = new IamSubjectEntity()
                .setId(idGenerator.nextId())
                .setDisplayName(displayName)
                .setStatus(SubjectStatus.ACTIVE);
        mapper.insertSubject(stored);
        return new IamSubjectEntity().setId(stored.getId()).setDisplayName(stored.getDisplayName()).setStatus(stored.getStatus()).setVersion(0L);
    }

    /**
     * 读取未删除的 Subject。
     *
     * @param subjectId 登录身份主键
     * @return 对应 Subject；不存在时为 {@code null}
     */
    public @Nullable IamSubjectEntity findSubject(@NotNull Long subjectId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(IamSubjectEntity.class)
                .where(IamSubjectEntity::getId)
                .eq(subjectId)
                .and(IamSubjectEntity::getDeleted)
                .eq(0);
        IamSubjectEntity stored = mapper.selectOneByQueryAs(wrapper, IamSubjectEntity.class);
        if (stored == null) {
            return null;
        }
        return new IamSubjectEntity().setId(stored.getId()).setDisplayName(stored.getDisplayName()).setStatus(stored.getStatus())
                .setVersion(stored.getVersion());
    }

    /**
     * 创建一个可登录的 Account。
     *
     * @param subjectId Account 所属 Subject
     * @param scopeType 登录标识的解析作用域
     * @param scopeId TENANT Account 所属租户；GLOBAL Account 为 null
     * @return 已分配主键并写入数据库的 Account
     * @throws IllegalArgumentException scopeType 与 scopeId 组合无效时抛出
     */
    public @NotNull IamAccountEntity createAccount(
            @NotNull Long subjectId,
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId) {
        if ((scopeType == AccountScopeType.GLOBAL && scopeId != null)
                || (scopeType == AccountScopeType.TENANT && scopeId == null)) {
            throw new IllegalArgumentException("Account 作用域类型与主键不匹配");
        }
        OffsetDateTime now = TimeSupport.now();
        IamAccountEntity account = new IamAccountEntity()
                .setId(idGenerator.nextId())
                .setSubjectId(subjectId)
                .setScopeType(scopeType)
                .setScopeId(scopeId)
                .setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(false)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setVersion(0L)
                .setDeleted(0);
        accountMapper.insert(account);
        return new IamAccountEntity().setId(account.getId()).setSubjectId(account.getSubjectId()).setScopeType(account.getScopeType())
                .setScopeId(account.getScopeId()).setStatus(account.getStatus()).setSecondFactorRequired(Boolean.TRUE.equals(account.getSecondFactorRequired()))
                .setVersion(account.getVersion());
    }

    /**
     * 读取未删除的 Account。
     *
     * @param accountId Account 主键
     * @return 当前 Account；不存在时为 null
     */
    public @Nullable IamAccountEntity findAccount(@NotNull Long accountId) {
        QueryWrapper query = QueryWrapper.create()
                .from(IamAccountEntity.class)
                .where(IamAccountEntity::getId)
                .eq(accountId)
                .and(IamAccountEntity::getDeleted)
                .eq(0);
        IamAccountEntity account = accountMapper.selectOneByQuery(query);
        if (account == null) {
            return null;
        }
        return new IamAccountEntity().setId(account.getId()).setSubjectId(account.getSubjectId()).setScopeType(account.getScopeType())
                .setScopeId(account.getScopeId()).setStatus(account.getStatus()).setSecondFactorRequired(Boolean.TRUE.equals(account.getSecondFactorRequired()))
                .setVersion(account.getVersion());
    }

    /**
     * 批量读取 Subject 名下未删除 Account。
     *
     * <p>固定执行一条 SQL，供 Subject 状态、角色和权限变化后刷新或关闭其所有 Account Session。
     *
     * @param subjectIds 去重后的 Subject 主键
     * @return 按 Subject、Account 主键排序的 Account
     */
    public @NotNull List<IamAccountEntity> findAccountsBySubjectIds(@NotNull Collection<Long> subjectIds) {
        if (subjectIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .from(IamAccountEntity.class)
                .where(IamAccountEntity::getSubjectId)
                .in(subjectIds)
                .and(IamAccountEntity::getDeleted)
                .eq(0)
                .orderBy(IamAccountEntity::getSubjectId)
                .asc()
                .orderBy(IamAccountEntity::getId)
                .asc();
        return accountMapper.selectListByQuery(query).stream()
                .map(account -> new IamAccountEntity().setId(account.getId()).setSubjectId(account.getSubjectId()).setScopeType(account.getScopeType())
                        .setScopeId(account.getScopeId()).setStatus(account.getStatus())
                        .setSecondFactorRequired(Boolean.TRUE.equals(account.getSecondFactorRequired())).setVersion(account.getVersion()))
                .toList();
    }

    /** 分页读取指定作用域中未删除的 Account。 */
    public @NotNull PageData<ManagedAccount> pageAccounts(
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId,
            @Nullable String search,
            @Nullable AccountStatus status,
            long page,
            long size) {
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(IamAccountEntity::getId).as(ManagedAccount::getAccountId),
                        column(IamAccountEntity::getSubjectId).as(ManagedAccount::getSubjectId),
                        column(IamPrincipalEntity::getId).as(ManagedAccount::getDefaultPrincipalId),
                        column(IamSubjectEntity::getDisplayName).as(ManagedAccount::getDisplayName),
                        column(IamAccountIdentifierEntity::getValue).as(ManagedAccount::getPrimaryIdentifier),
                        column(IamDefaultProfileEntity::getAvatarFileId).as(ManagedAccount::getAvatarFileId),
                        column(IamSubjectEntity::getVersion).as(ManagedAccount::getSubjectVersion),
                        column(IamDefaultProfileEntity::getVersion).as(ManagedAccount::getProfileVersion),
                        column(IamAccountEntity::getScopeType).as(ManagedAccount::getScopeType),
                        column(IamAccountEntity::getScopeId).as(ManagedAccount::getScopeId),
                        column(IamAccountEntity::getStatus).as(ManagedAccount::getStatus),
                        column(IamAccountEntity::getSecondFactorRequired).as(ManagedAccount::isSecondFactorRequired),
                        column(IamAccountEntity::getVersion).as(ManagedAccount::getVersion),
                        column(IamAccountEntity::getCreatedAt).as(ManagedAccount::getCreatedAt),
                        column(IamAccountEntity::getUpdatedAt).as(ManagedAccount::getUpdatedAt))
                .from(IamAccountEntity.class)
                .innerJoin(IamSubjectEntity.class)
                .on(join -> join.where(IamSubjectEntity::getId)
                        .eq(IamAccountEntity::getSubjectId)
                        .and(IamSubjectEntity::getDeleted)
                        .eq(0))
                .innerJoin(IamPrincipalEntity.class)
                .on(join -> join.where(IamPrincipalEntity::getSubjectId)
                        .eq(IamAccountEntity::getSubjectId)
                        .and(IamPrincipalEntity::getPrincipalType)
                        .eq(PrincipalType.DEFAULT)
                        .and(IamPrincipalEntity::getTenantId)
                        .isNull()
                        .and(IamPrincipalEntity::getDeleted)
                        .eq(0))
                .innerJoin(IamDefaultProfileEntity.class)
                .on(join -> join.where(IamDefaultProfileEntity::getPrincipalId)
                        .eq(IamPrincipalEntity::getId)
                        .and(IamDefaultProfileEntity::getDeleted)
                        .eq(0))
                .leftJoin(IamAccountIdentifierEntity.class)
                .on(join -> join.where(IamAccountIdentifierEntity::getAccountId)
                        .eq(IamAccountEntity::getId)
                        .and(IamAccountIdentifierEntity::getIdentifierType)
                        .eq(AccountIdentifierType.USERNAME)
                        .and(IamAccountIdentifierEntity::getStatus)
                        .eq(AccountIdentifierStatus.ACTIVE)
                        .and(IamAccountIdentifierEntity::getDeleted)
                        .eq(0))
                .where(IamAccountEntity::getScopeType).eq(scopeType)
                .and(IamAccountEntity::getDeleted).eq(0)
                .and(IamAccountEntity::getStatus).eq(status, Objects::nonNull);
        if (StringUtils.hasText(search)) {
            String keyword = search.trim().toLowerCase(java.util.Locale.ROOT);
            query.and(condition -> {
                condition.where(lower(column(IamSubjectEntity::getDisplayName)).like(keyword))
                        .or(lower(column(IamAccountIdentifierEntity::getValue)).like(keyword));
                try {
                    condition.or(column(IamAccountEntity::getId).eq(Long.valueOf(keyword)));
                } catch (NumberFormatException ignored) {
                    // 非数字关键字只匹配展示名称和用户名。
                }
            });
        }
        if (scopeId == null) {
            query.and(IamAccountEntity::getScopeId).isNull();
        } else {
            query.and(IamAccountEntity::getScopeId).eq(scopeId);
        }
        query.orderBy(IamAccountEntity::getCreatedAt).desc().orderBy(IamAccountEntity::getId).desc();
        return PageData.from(accountMapper.paginateAs(page, size, query, ManagedAccount.class));
    }

    /**
     * 使用 Account 版本原子修改创建 Session 前的第二因子要求。
     *
     * @param accountId Account 主键
     * @param required 是否必须完成第二认证因素
     * @param expectedVersion 调用方读取的 Account 版本
     */
    public void changeSecondFactorRequirement(
            @NotNull Long accountId, boolean required, long expectedVersion) {
        IamAccountEntity update = new IamAccountEntity()
                .setSecondFactorRequired(required)
                .setUpdatedAt(TimeSupport.now())
                .setVersion(expectedVersion + 1);
        QueryWrapper condition = QueryWrapper.create()
                .where(IamAccountEntity::getId)
                .eq(accountId)
                .and(IamAccountEntity::getVersion)
                .eq(expectedVersion)
                .and(IamAccountEntity::getDeleted)
                .eq(0);
        if (accountMapper.updateByQuery(update, condition) != 1) {
            throw new ConcurrencyConflictException(IamErrorCode.ACCOUNT_CHANGED, IamClientStateTargets.ACCOUNTS);
        }
    }

    /** 使用 Account 版本原子修改登录状态。 */
    public void updateAccountStatus(
            @NotNull Long accountId, @NotNull AccountStatus status, long expectedVersion) {
        IamAccountEntity update = new IamAccountEntity()
                .setStatus(status)
                .setUpdatedAt(TimeSupport.now())
                .setVersion(expectedVersion + 1);
        QueryWrapper condition = QueryWrapper.create()
                .where(IamAccountEntity::getId)
                .eq(accountId)
                .and(IamAccountEntity::getVersion)
                .eq(expectedVersion)
                .and(IamAccountEntity::getDeleted)
                .eq(0);
        if (accountMapper.updateByQuery(update, condition) != 1) {
            throw new ConcurrencyConflictException(IamErrorCode.ACCOUNT_CHANGED, IamClientStateTargets.ACCOUNTS);
        }
    }

    /** 判断移除指定 Account 后是否会让 ACTIVE 非 DEFAULT Principal 失去登录入口。 */
    public boolean hasUnreachableActivePrincipal(
            @NotNull Long subjectId, @NotNull Long excludedAccountId) {
        return mapper.hasUnreachableActivePrincipal(subjectId, excludedAccountId);
    }

    /** 按 Account 版本隔离账号及其登录标识和认证凭据。 */
    public void isolateAccount(@NotNull Long accountId, long version) {
        if (mapper.isolateAccount(accountId, version) == 0) {
            throw new ConcurrencyConflictException(IamErrorCode.ACCOUNT_CHANGED, IamClientStateTargets.ACCOUNTS);
        }
    }

    /**
     * 批量读取资源引用需要的 Subject 安全投影。
     *
     * <p>固定执行一条单表 SQL，包含已逻辑删除行以区分“已删除”和“从未存在”；不读取联系方式等无关资料。
     *
     * @param subjectIds 去重后的 Subject 主键
     * @return Subject 主键、展示名称和删除状态投影
     */
    public @NotNull List<SubjectReference> findSubjectReferences(@NotNull Set<Long> subjectIds) {
        if (subjectIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(IamSubjectEntity::getId),
                        column(IamSubjectEntity::getDisplayName),
                        column(IamSubjectEntity::getDeleted))
                .from(IamSubjectEntity.class)
                .where(IamSubjectEntity::getId)
                .in(subjectIds);
        return mapper.selectListByQueryAs(query, IamSubjectEntity.class).stream()
                .map(stored -> new SubjectReference(
                        stored.getId(), stored.getDisplayName(), !Integer.valueOf(0).equals(stored.getDeleted())))
                .toList();
    }

    /** 批量读取 Account 资源引用，不读取认证材料。 */
    public @NotNull List<AccountReference> findAccountReferences(@NotNull Set<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(IamAccountEntity::getId).as(AccountReference::getAccountId),
                        column(IamSubjectEntity::getDisplayName).as(AccountReference::getDisplayName),
                        column(IamAccountEntity::getScopeType).as(AccountReference::getScopeType),
                        column(IamAccountEntity::getScopeId).as(AccountReference::getScopeId),
                        column(IamAccountEntity::getDeleted).as(AccountReference::isDeleted))
                .from(IamAccountEntity.class)
                .leftJoin(IamSubjectEntity.class)
                .on(IamSubjectEntity::getId, IamAccountEntity::getSubjectId)
                .where(IamAccountEntity::getId)
                .in(accountIds);
        return accountMapper.selectListByQueryAs(query, AccountReference.class);
    }

    /**
     * 批量读取资源引用需要的租户安全投影。
     *
     * <p>固定执行一条单表 SQL，包含已逻辑删除行；不加载成员、角色或字典关系。
     *
     * @param tenantIds 去重后的租户主键
     * @return 租户主键、业务名称和删除状态投影
     */
    public @NotNull List<TenantReference> findTenantReferences(@NotNull Set<Long> tenantIds) {
        if (tenantIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(IamTenantEntity::getId),
                        column(IamTenantEntity::getName),
                        column(IamTenantEntity::getDeleted))
                .from(IamTenantEntity.class)
                .where(IamTenantEntity::getId)
                .in(tenantIds);
        return mapper.selectListByQueryAs(query, IamTenantEntity.class).stream()
                .map(stored -> new TenantReference(
                        stored.getId(), stored.getName(), !Integer.valueOf(0).equals(stored.getDeleted())))
                .toList();
    }

    /**
     * 批量读取资源引用需要的 Principal 安全投影。
     *
     * <p>固定执行一条单表 SQL；返回租户归属用于生成作用域明确的成员详情路径。
     *
     * @param principalIds 去重后的 Principal 主键
     * @return Principal 主键、Subject、租户、展示名称和删除状态投影
     */
    public @NotNull List<PrincipalReference> findPrincipalReferences(@NotNull Set<Long> principalIds) {
        if (principalIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(IamPrincipalEntity::getId),
                        column(IamPrincipalEntity::getSubjectId),
                        column(IamPrincipalEntity::getTenantId),
                        column(IamPrincipalEntity::getPrincipalType),
                        column(IamPrincipalEntity::getDisplayName),
                        column(IamPrincipalEntity::getDeleted))
                .from(IamPrincipalEntity.class)
                .where(IamPrincipalEntity::getId)
                .in(principalIds);
        return mapper.selectListByQueryAs(query, IamPrincipalEntity.class).stream()
                .map(stored -> new PrincipalReference(
                        stored.getId(),
                        stored.getSubjectId(),
                        stored.getTenantId(),
                        stored.getPrincipalType(),
                        stored.getDisplayName(),
                        !Integer.valueOf(0).equals(stored.getDeleted())))
                .toList();
    }

    /**
     * 批量读取资源引用需要的 Credential 安全投影。
     *
     * <p>固定执行一条 Credential 与 Account JOIN，只选择主键、归属、类型、登录账号和删除状态；摘要、盐和 Token 不进入查询结果。
     *
     * @param credentialIds 去重后的 Credential 主键
     * @return 不含任何认证秘密的 Credential 引用投影
     */
    public @NotNull List<CredentialReference> findCredentialReferences(@NotNull Set<Long> credentialIds) {
        if (credentialIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectCredentialReferenceRows(credentialIds).stream()
                .map(row -> new CredentialReference(
                        Objects.requireNonNull(row.getCredentialId(), "Credential 引用行缺少主键"),
                        Objects.requireNonNull(row.getAccountId(), "Credential 引用行缺少 Account"),
                        Objects.requireNonNull(row.getSubjectId(), "Credential 引用行缺少 Subject"),
                        Objects.requireNonNull(row.getCredentialType(), "Credential 引用行缺少类型"),
                        row.getIdentifier(),
                        Objects.requireNonNull(row.getDeleted(), "Credential 引用行缺少删除状态")))
                .toList();
    }

    /**
     * 批量读取资源引用需要的角色安全投影。
     *
     * <p>固定执行一条单表 SQL，返回角色范围、名称、编码和删除状态，不加载权限或 Principal 关系。
     *
     * @param roleIds 去重后的角色主键
     * @return 角色主键、租户、编码、名称和删除状态投影
     */
    public @NotNull List<RoleReference> findRoleReferences(@NotNull Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(IamRoleEntity::getId),
                        column(IamRoleEntity::getTenantId),
                        column(IamRoleEntity::getCode),
                        column(IamRoleEntity::getName),
                        column(IamRoleEntity::getDeleted))
                .from(IamRoleEntity.class)
                .where(IamRoleEntity::getId)
                .in(roleIds);
        return mapper.selectListByQueryAs(query, IamRoleEntity.class).stream()
                .map(stored -> new RoleReference(
                        stored.getId(),
                        stored.getTenantId(),
                        stored.getCode(),
                        stored.getName(),
                        !Integer.valueOf(0).equals(stored.getDeleted())))
                .toList();
    }

    /**
     * 原子更新 Subject 的可编辑资料。
     *
     * @param subjectId 需要修改的 Subject 主键
     * @param displayName 新展示名称；允许清空
     * @param version 调用方读取到的乐观锁版本
     * @throws ConcurrencyConflictException Subject 已删除或版本已变化时抛出
     */
    public void updateSubject(
            @NotNull Long subjectId,
            @Nullable String displayName,
            @NotNull Long version) {
        if (mapper.updateSubject(subjectId, displayName, version) == 0) {
            throw new ConcurrencyConflictException(IamErrorCode.SUBJECT_CHANGED, ClientStateTarget.resource(IamResourceTypes.SUBJECT, subjectId));
        }
    }

    /**
     * 原子更新 Subject 状态。
     *
     * @param subjectId 需要修改的 Subject 主键
     * @param status 目标启用、停用或删除状态
     * @param version 调用方读取到的乐观锁版本
     * @throws ConcurrencyConflictException Subject 已删除或版本已变化时抛出
     */
    public void updateSubjectStatus(
            @NotNull Long subjectId, @NotNull SubjectStatus status, @NotNull Long version) {
        if (mapper.updateSubjectStatus(subjectId, status, version) == 0) {
            throw new ConcurrencyConflictException(IamErrorCode.SUBJECT_STATUS_CHANGED, ClientStateTarget.resource(IamResourceTypes.SUBJECT, subjectId));
        }
    }

    /**
     * 在用户聚合敏感数据变更前原子推进 Subject 版本。
     *
     * @param subjectId Subject 主键
     * @param version 调用方读取到的 Subject 版本
     */
    public void advanceSubjectVersion(@NotNull Long subjectId, @NotNull Long version) {
        if (mapper.advanceSubjectVersion(subjectId, version) == 0) {
            throw new ConcurrencyConflictException(IamErrorCode.SUBJECT_CHANGED, ClientStateTarget.resource(IamResourceTypes.SUBJECT, subjectId));
        }
    }

    /**
     * 同步隔离一个 Subject，并登记可重试的关系清理任务。
     *
     * <p>Subject、Credential 和 Principal 在同一事务内进入终态删除，使认证、主体选择和授权查询立即失败。
     * 关系表和已隔离明细由后续集群任务幂等清理。
     *
     * @param subjectId 待最终删除的登录身份主键
     * @param version 删除请求读取到的 Subject 版本
     * @throws ConcurrencyConflictException Subject 已删除或版本已变化时抛出
     */
    public void isolateDeletedSubject(@NotNull Long subjectId, @NotNull Long version) {
        if (mapper.deleteSubject(subjectId, version) == 0) {
            throw new ConcurrencyConflictException(IamErrorCode.SUBJECT_CHANGED, ClientStateTarget.resource(IamResourceTypes.SUBJECT, subjectId));
        }
        mapper.isolateAccountsBySubject(subjectId);
        mapper.deleteMembershipsBySubject(subjectId);
        mapper.deletePrincipalsBySubject(subjectId);
        mapper.insertSubjectCleanupTask(idGenerator.nextId(), subjectId);
    }

    /**
     * 原子领取一批尚未清理的 Subject 任务。
     *
     * @param limit 当前节点本轮允许领取的最大任务数
     * @return 已标记为处理中且只属于当前领取事务的任务
     */
    public @NotNull List<SubjectCleanupTask> claimSubjectCleanupTasks(int limit) {
        return mapper.claimPendingSubjectCleanupTasks(limit);
    }

    /**
     * 硬删除一个已隔离 Subject 的 IAM 关系和认证明细。
     *
     * @param subjectId 已经进入终态删除的登录身份主键
     */
    public void deleteSubjectRelations(@NotNull Long subjectId) {
        mapper.deleteIsolatedSubjectRelations(subjectId);
    }

    /**
     * 读取已隔离 Subject 仍保留的 Principal 主键。
     *
     * @param subjectId 已进入删除终态的 Subject 主键
     * @return 供外围运行时关系清理使用的固定主键集合
     */
    public @NotNull List<Long> findIsolatedSubjectPrincipalIds(@NotNull Long subjectId) {
        return mapper.selectIsolatedSubjectPrincipalIds(subjectId);
    }

    /**
     * 将成功清理的任务标记为完成。
     *
     * @param taskId 清理任务主键
     */
    public void completeSubjectCleanup(@NotNull Long taskId) {
        mapper.completeCleanupTask(taskId);
    }

    /**
     * 释放执行失败的任务，保留错误摘要并允许后续节点重试。
     *
     * @param taskId 清理任务主键
     * @param error 本轮失败的可观测摘要
     */
    public void releaseSubjectCleanup(@NotNull Long taskId, @NotNull String error) {
        mapper.releaseCleanupTask(taskId, error);
    }

    /**
     * 原子领取一批尚未清理的 Tenant 任务。
     *
     * @param limit 当前节点本轮允许领取的最大任务数
     * @return 已标记为处理中且只属于当前领取事务的任务
     */
    public @NotNull List<TenantCleanupTask> claimTenantCleanupTasks(int limit) {
        return mapper.claimPendingTenantCleanupTasks(limit);
    }

    /**
     * 读取已隔离租户仍保留的 TENANT Principal 主键。
     *
     * @param tenantId 已进入删除终态的租户主键
     * @return 供外围运行时关系清理使用的固定主键集合
     */
    public @NotNull List<Long> findIsolatedTenantPrincipalIds(@NotNull Long tenantId) {
        return mapper.selectIsolatedTenantPrincipalIds(tenantId);
    }

    /**
     * 硬删除已隔离租户的 IAM 运行时关系。
     *
     * @param tenantId 已进入删除终态的租户主键
     */
    public void deleteTenantRelations(@NotNull Long tenantId) {
        mapper.deleteIsolatedTenantRelations(tenantId);
    }

    /** @param taskId 已成功清理的 Tenant 任务主键 */
    public void completeTenantCleanup(@NotNull Long taskId) {
        mapper.completeCleanupTask(taskId);
    }

    /** @param taskId 执行失败的 Tenant 任务主键 @param error 本轮失败摘要 */
    public void releaseTenantCleanup(@NotNull Long taskId, @NotNull String error) {
        mapper.releaseCleanupTask(taskId, error);
    }

    /**
     * 创建使用独立集群主键的 Principal。
     *
     * @param subjectId Principal 所属登录身份
     * @param principalType 业务主体类型
     * @param tenantId TENANT Principal 所属租户；非租户主体不提供
     * @param displayName Principal 的独立展示名称
     * @return 已持久化的 Principal
     */
    public @NotNull IamPrincipalEntity createPrincipal(
            @NotNull Long subjectId,
            @NotNull PrincipalType principalType,
            @Nullable Long tenantId,
            @NotNull String displayName) {
        IamPrincipalEntity stored = new IamPrincipalEntity()
                .setId(idGenerator.nextId())
                .setSubjectId(subjectId)
                .setTenantId(tenantId)
                .setPrincipalType(principalType)
                .setDisplayName(displayName)
                .setStatus(PrincipalStatus.ACTIVE);
        try {
            mapper.insertPrincipal(stored);
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", "uk_iam_principal_subject_type_tenant")) {
                throw new BizException(IamErrorCode.PRINCIPAL_EXISTS);
            }
            throw exception;
        }
        return new IamPrincipalEntity().setId(stored.getId()).setSubjectId(stored.getSubjectId()).setTenantId(stored.getTenantId())
                .setMembershipId(stored.getMembershipId()).setPrincipalType(stored.getPrincipalType()).setDisplayName(stored.getDisplayName())
                .setStatus(stored.getStatus()).setVersion(0L);
    }

    /**
     * 创建租户 Membership 及其唯一 TENANT Principal。
     *
     * <p>两条写入必须位于调用方事务中。Membership 保存加入和启停状态，Principal 只保存角色与执行主体状态。
     *
     * @param subjectId 加入租户的 Subject 主键
     * @param tenantId 目标 Tenant 主键
     * @param displayName TENANT Principal 展示名称
     * @return 已关联 Membership 的 TENANT Principal
     * @throws BizException 同一 Subject 已存在该租户 Membership 时抛出
     */
    public @NotNull IamPrincipalEntity createTenantPrincipal(
            @NotNull Long subjectId,
            @NotNull Long tenantId,
            @NotNull String displayName) {
        OffsetDateTime now = TimeSupport.now();
        IamTenantMembershipEntity membership = new IamTenantMembershipEntity()
                .setId(idGenerator.nextId())
                .setSubjectId(subjectId)
                .setTenantId(tenantId)
                .setStatus(MembershipStatus.ACTIVE)
                .setJoinedAt(now)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setVersion(0L)
                .setDeleted(0);
        IamPrincipalEntity stored = new IamPrincipalEntity()
                .setId(idGenerator.nextId())
                .setSubjectId(subjectId)
                .setTenantId(tenantId)
                .setMembershipId(membership.getId())
                .setPrincipalType(PrincipalType.TENANT)
                .setDisplayName(displayName)
                .setStatus(PrincipalStatus.ACTIVE);
        try {
            mapper.insertTenantMembership(membership);
            mapper.insertPrincipal(stored);
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", "uk_iam_tenant_membership_subject_tenant")
                    || PostgresConstraintViolation.matches(exception, "23505", "uk_iam_principal_subject_type_tenant")
                    || PostgresConstraintViolation.matches(exception, "23505", "uk_iam_principal_membership")) {
                throw new BizException(IamErrorCode.PRINCIPAL_EXISTS);
            }
            throw exception;
        }
        return new IamPrincipalEntity().setId(stored.getId()).setSubjectId(stored.getSubjectId()).setTenantId(stored.getTenantId())
                .setMembershipId(stored.getMembershipId()).setPrincipalType(stored.getPrincipalType()).setDisplayName(stored.getDisplayName())
                .setStatus(stored.getStatus()).setVersion(0L);
    }

    /**
     * 为租户 ACL 完整替换取得 PLATFORM Principal 的乐观写入权。
     *
     * @param principalId 目标 PLATFORM Principal 主键
     * @param version 调用方读取到的当前版本
     */
    public void advancePlatformPrincipalVersionForTenantAccess(
            @NotNull Long principalId, @NotNull Long version) {
        if (mapper.advancePlatformPrincipalVersion(principalId, version) != 1) {
            throw new ConcurrencyConflictException(TenantErrorCode.TENANT_ACCESS_CHANGED, ClientStateTarget.resource(IamResourceTypes.PRINCIPAL, principalId));
        }
    }

    /**
     * 批量确认指定租户仍有效，可作为限定数据范围数据保存。
     *
     * <p>固定执行一条单表 SQL；调用方以输入去重数量比对结果，避免逐租户回查。
     *
     * @param tenantIds 已去重的租户主键集合
     * @return 当前未删除租户主键集合
     */
    public @NotNull Set<Long> findExistingTenantIds(@NotNull Set<Long> tenantIds) {
        if (tenantIds.isEmpty()) {
            return Set.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(column(IamTenantEntity::getId))
                .from(IamTenantEntity.class)
                .where(IamTenantEntity::getId)
                .in(tenantIds)
                .and(IamTenantEntity::getDeleted)
                .eq(0);
        return Set.copyOf(mapper.selectObjectListByQueryAs(query, Long.class));
    }

    /**
     * 创建使用独立集群主键的默认 Principal。
     *
     * @param subjectId Principal 所属登录身份
     * @param displayName Principal 展示名称
     * @return 已持久化的默认 Principal
     */
    public @NotNull IamPrincipalEntity createDefaultPrincipal(
            @NotNull Long subjectId, @NotNull String displayName) {
        IamPrincipalEntity stored = new IamPrincipalEntity()
                .setId(idGenerator.nextId())
                .setSubjectId(subjectId)
                .setPrincipalType(PrincipalType.DEFAULT)
                .setDisplayName(displayName)
                .setStatus(PrincipalStatus.ACTIVE);
        try {
            mapper.insertPrincipal(stored);
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", "uk_iam_principal_subject_type_tenant")) {
                throw new BizException(IamErrorCode.PRINCIPAL_EXISTS);
            }
            throw exception;
        }
        return new IamPrincipalEntity().setId(stored.getId()).setSubjectId(stored.getSubjectId()).setTenantId(stored.getTenantId())
                .setMembershipId(stored.getMembershipId()).setPrincipalType(stored.getPrincipalType()).setDisplayName(stored.getDisplayName())
                .setStatus(stored.getStatus()).setVersion(0L);
    }

    /**
     * 查找同一 Subject 在指定业务范围内的 Principal。
     *
     * @param subjectId 登录身份主键
     * @param principalType 业务主体类型
     * @param tenantId TENANT Principal 的租户主键；非租户主体不提供
     * @return 匹配的 Principal；不存在时为 {@code null}
     */
    public @Nullable IamPrincipalEntity findPrincipal(
            @NotNull Long subjectId,
            @NotNull PrincipalType principalType,
            @Nullable Long tenantId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(IamPrincipalEntity.class)
                .where(IamPrincipalEntity::getSubjectId)
                .eq(subjectId)
                .and(IamPrincipalEntity::getPrincipalType)
                .eq(principalType)
                .and(IamPrincipalEntity::getDeleted)
                .eq(0);
        if (tenantId == null) {
            wrapper.and(IamPrincipalEntity::getTenantId).isNull();
        } else {
            wrapper.and(IamPrincipalEntity::getTenantId).eq(tenantId);
        }
        wrapper.orderBy(IamPrincipalEntity::getId).desc();
        IamPrincipalEntity stored = mapper.selectOneByQueryAs(wrapper, IamPrincipalEntity.class);
        if (stored == null) {
            return null;
        }
        return new IamPrincipalEntity().setId(stored.getId()).setSubjectId(stored.getSubjectId()).setTenantId(stored.getTenantId())
                .setMembershipId(stored.getMembershipId()).setPrincipalType(stored.getPrincipalType()).setDisplayName(stored.getDisplayName())
                .setStatus(stored.getStatus()).setVersion(stored.getVersion());
    }

    /**
     * 读取 Principal。
     *
     * @param principalId Principal 主键
     * @return 未删除的 Principal；不存在时为 {@code null}
     */
    public @Nullable IamPrincipalEntity findPrincipalById(@NotNull Long principalId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(IamPrincipalEntity.class)
                .where(IamPrincipalEntity::getId)
                .eq(principalId)
                .and(IamPrincipalEntity::getDeleted)
                .eq(0);
        IamPrincipalEntity stored = mapper.selectOneByQueryAs(wrapper, IamPrincipalEntity.class);
        if (stored == null) {
            return null;
        }
        return new IamPrincipalEntity().setId(stored.getId()).setSubjectId(stored.getSubjectId()).setTenantId(stored.getTenantId())
                .setMembershipId(stored.getMembershipId()).setPrincipalType(stored.getPrincipalType()).setDisplayName(stored.getDisplayName())
                .setStatus(stored.getStatus()).setVersion(stored.getVersion());
    }

    /**
     * 读取租户状态变化时需要失效 Principal 会话快照的全部 Subject。
     *
     * @param tenantId 租户主键
     * @return 去重且固定排序的 Subject 主键
     */
    public @NotNull List<Long> findTenantSubjectIds(@NotNull Long tenantId) {
        return mapper.selectTenantSubjectIds(tenantId);
    }

    /**
     * 用一次数据库查询读取多个 Account 的完整登录会话 JOIN 行。
     *
     * <p>GLOBAL Account 返回 Subject 的全部可达 Principal；TENANT Account 只返回 DEFAULT 与同租户
     * Principal。结果由会话 Service 按 Account 和 Principal 聚合。
     *
     * @param accountIds 去重后的 Account 主键
     * @return 顺序固定的会话 JOIN 行
     */
    public @NotNull List<SessionRow> findSessionRows(@NotNull List<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectSessionRows(accountIds);
    }

    /**
     * 统计指定类型的未删除 Principal。
     *
     * @param principalType 要统计的 Principal 类型
     * @return 当前记录数量
     */
    public long countPrincipals(@NotNull PrincipalType principalType) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(IamPrincipalEntity.class)
                .where(IamPrincipalEntity::getPrincipalType)
                .eq(principalType)
                .and(IamPrincipalEntity::getDeleted)
                .eq(0);
        return mapper.selectCountByQuery(wrapper);
    }

    /**
     * 原子更新 Principal 状态。
     *
     * @param principalId Principal 主键
     * @param status 目标状态
     * @param version 调用方读取到的乐观锁版本
     * @throws ConcurrencyConflictException Principal 已删除或版本已变化时抛出
     */
    public void updatePrincipalStatus(
            @NotNull Long principalId, @NotNull PrincipalStatus status, @NotNull Long version) {
        if (mapper.updatePrincipalStatus(principalId, status, version) == 0) {
            throw new ConcurrencyConflictException(IamErrorCode.PRINCIPAL_STATUS_CHANGED, ClientStateTarget.resource(IamResourceTypes.PRINCIPAL, principalId));
        }
    }

    /**
     * 在 Principal 仍属于指定租户时原子更新成员状态。
     *
     * @param principalId TENANT Principal 主键
     * @param tenantId 所属租户主键
     * @param status 目标状态
     * @param version 调用方读取到的乐观锁版本
     * @throws ConcurrencyConflictException Principal 已删除、租户不匹配或版本已变化时抛出
     */
    public void updateTenantMembershipStatus(
            @NotNull Long membershipId,
            @NotNull Long tenantId,
            @NotNull MembershipStatus status,
            @NotNull Long version) {
        if (mapper.updateTenantMembershipStatus(membershipId, tenantId, status, version) == 0) {
            throw new ConcurrencyConflictException(
                    TenantErrorCode.TENANT_MEMBER_STATUS_CHANGED,
                    ClientStateTarget.resource(IamResourceTypes.MEMBERSHIP, membershipId));
        }
    }

    /**
     * 读取属于指定租户且可参与绑定的角色。
     *
     * @param tenantId 租户主键
     * @param roleIds 待校验的角色主键
     * @return 已启用且属于该租户的角色列表
     */
    public @NotNull Set<Long> findActiveTenantRoleIds(
            @NotNull Long tenantId, @NotNull List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        QueryWrapper wrapper = QueryWrapper.create()
                .select(column(IamRoleEntity::getId))
                .from(IamRoleEntity.class)
                .where(IamRoleEntity::getTenantId)
                .eq(tenantId)
                .and(IamRoleEntity::getId)
                .in(roleIds)
                .and(IamRoleEntity::getStatus)
                .eq(RoleStatus.ACTIVE)
                .and(IamRoleEntity::getDeleted)
                .eq(0)
                .orderBy(IamRoleEntity::getId)
                .asc();
        return Set.copyOf(mapper.selectObjectListByQueryAs(wrapper, Long.class));
    }

    /**
     * 读取可绑定给 PLATFORM Principal 的启用平台角色。
     *
     * @param roleIds 待校验的平台角色主键
     * @return 仍有效且没有租户归属的角色集合
     */
    public @NotNull Set<Long> findActivePlatformRoleIds(@NotNull List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        QueryWrapper wrapper = QueryWrapper.create()
                .select(column(IamRoleEntity::getId))
                .from(IamRoleEntity.class)
                .where(IamRoleEntity::getTenantId)
                .isNull()
                .and(IamRoleEntity::getId)
                .in(roleIds)
                .and(IamRoleEntity::getStatus)
                .eq(RoleStatus.ACTIVE)
                .and(IamRoleEntity::getDeleted)
                .eq(0);
        return Set.copyOf(mapper.selectObjectListByQueryAs(wrapper, Long.class));
    }

    /**
     * 批量读取 Subject 对应的 PLATFORM Principal 及当前有效平台角色。
     *
     * <p>空集合执行 0 条 SQL；非空集合固定执行 1 条 Principal 查询，并在存在主体时追加 1 条角色查询，
     * 不随用户数量形成 N+1。
     *
     * @param subjectIds 需要加载平台主体的 Subject 主键集合
     * @return 按 Subject 主键索引的平台主体
     */
    public @NotNull Map<Long, PlatformPrincipal> findPlatformPrincipalsBySubjectIds(
            @NotNull Collection<Long> subjectIds) {
        List<Long> distinctSubjectIds = subjectIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctSubjectIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper wrapper = QueryWrapper.create()
                .from(IamPrincipalEntity.class)
                .where(IamPrincipalEntity::getSubjectId)
                .in(distinctSubjectIds)
                .and(IamPrincipalEntity::getPrincipalType)
                .eq(PrincipalType.PLATFORM)
                .and(IamPrincipalEntity::getDeleted)
                .eq(0)
                .orderBy(IamPrincipalEntity::getId)
                .asc();
        Map<Long, PlatformPrincipal> principalsBySubject = new LinkedHashMap<>();
        Map<Long, PlatformPrincipal> principalsById = new LinkedHashMap<>();
        for (IamPrincipalEntity stored : mapper.selectListByQueryAs(wrapper, IamPrincipalEntity.class)) {
            PlatformPrincipal principal = new PlatformPrincipal()
                    .setPrincipalId(stored.getId())
                    .setSubjectId(stored.getSubjectId())
                    .setDisplayName(stored.getDisplayName())
                    .setStatus(stored.getStatus())
                    .setVersion(stored.getVersion());
            principalsBySubject.put(stored.getSubjectId(), principal);
            principalsById.put(stored.getId(), principal);
        }
        if (!principalsById.isEmpty()) {
            for (PlatformRole role : mapper.selectPlatformPrincipalRoles(List.copyOf(principalsById.keySet()))) {
                PlatformPrincipal principal = principalsById.get(role.getPrincipalId());
                if (principal != null) {
                    principal.getRoles().add(role);
                }
            }
        }
        return Map.copyOf(principalsBySubject);
    }

    /** 分页读取所有未删除 PLATFORM Principal 及其角色。 */
    public @NotNull PageData<PlatformPrincipal> pagePlatformPrincipals(long page, long size) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(IamPrincipalEntity.class)
                .where(IamPrincipalEntity::getPrincipalType)
                .eq(PrincipalType.PLATFORM)
                .and(IamPrincipalEntity::getDeleted)
                .eq(0)
                .orderBy(IamPrincipalEntity::getCreatedAt)
                .desc()
                .orderBy(IamPrincipalEntity::getId)
                .desc();
        PageData<IamPrincipalEntity> principals = PageData.from(
                mapper.paginateAs(page, size, wrapper, IamPrincipalEntity.class));
        Map<Long, PlatformPrincipal> bySubject = findPlatformPrincipalsBySubjectIds(
                principals.getRecords().stream().map(IamPrincipalEntity::getSubjectId).toList());
        return principals.map(principal -> bySubject.get(principal.getSubjectId()));
    }

    /**
     * 读取当前可以分配给 PLATFORM Principal 的启用平台角色。
     *
     * @return 按名称、编码和主键固定排序的平台角色选择项
     */
    public @NotNull List<PlatformRole> findAssignablePlatformRoles() {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(IamRoleEntity.class)
                .where(IamRoleEntity::getTenantId)
                .isNull()
                .and(IamRoleEntity::getStatus)
                .eq(RoleStatus.ACTIVE)
                .and(IamRoleEntity::getDeleted)
                .eq(0)
                .orderBy(IamRoleEntity::getName)
                .asc()
                .orderBy(IamRoleEntity::getCode)
                .asc()
                .orderBy(IamRoleEntity::getId)
                .asc();
        return mapper.selectListByQueryAs(wrapper, IamRoleEntity.class).stream()
                .map(role -> new PlatformRole()
                        .setRoleId(role.getId())
                        .setRoleCode(role.getCode())
                        .setRoleName(role.getName()))
                .toList();
    }

    /**
     * 读取可以绑定给指定租户 TENANT Principal 的启用角色。
     *
     * @param tenantId 租户主键
     * @return 按名称、编码和主键固定排序的角色选择项
     */
    public @NotNull List<TenantRole> findAssignableTenantRoles(@NotNull Long tenantId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(IamRoleEntity.class)
                .where(IamRoleEntity::getTenantId)
                .eq(tenantId)
                .and(IamRoleEntity::getStatus)
                .eq(RoleStatus.ACTIVE)
                .and(IamRoleEntity::getDeleted)
                .eq(0)
                .orderBy(IamRoleEntity::getName)
                .asc()
                .orderBy(IamRoleEntity::getCode)
                .asc()
                .orderBy(IamRoleEntity::getId)
                .asc();
        return mapper.selectListByQueryAs(wrapper, IamRoleEntity.class).stream()
                .map(role -> new TenantRole(role.getId(), role.getCode(), role.getName()))
                .toList();
    }

    /**
     * 用给定角色集合替换 Principal 的全部角色关系。
     *
     * <p>删除旧关系与插入新关系属于同一个固定的关系存储操作。调用方应在外层事务中先完成业务角色范围校验。
     *
     * @param principalId 需要替换角色的 Principal 主键
     * @param roleIds 去重后的目标角色主键列表
     */
    public void replacePrincipalRoles(@NotNull Long principalId, @NotNull List<Long> roleIds) {
        mapper.deletePrincipalRoles(principalId);
        if (roleIds.isEmpty()) {
            return;
        }
        List<IamPrincipalRoleEntity> relations = roleIds.stream()
                .map(roleId -> new IamPrincipalRoleEntity()
                        .setId(idGenerator.nextId())
                        .setPrincipalId(principalId)
                        .setRoleId(roleId))
                .toList();
        if (mapper.insertPrincipalRoles(relations) != relations.size()) {
            throw new IllegalStateException("Principal 角色关系写入行数异常");
        }
    }

    /**
     * 在 TENANT Principal 仍属于指定租户且版本匹配时替换全部角色关系。
     *
     * @param principalId 需要替换角色的 TENANT Principal 主键
     * @param tenantId 所属租户主键
     * @param roleIds 已校验并去重的目标角色主键
     * @param version 调用方读取到的 Principal 版本
     */
    public void replaceTenantPrincipalRoles(
            @NotNull Long principalId,
            @NotNull Long tenantId,
            @NotNull List<Long> roleIds,
            @NotNull Long version) {
        if (mapper.advanceTenantPrincipalVersion(principalId, tenantId, version) == 0) {
            throw new ConcurrencyConflictException(RoleErrorCode.TENANT_MEMBER_ROLES_CHANGED,
                    ClientStateTarget.resource(IamResourceTypes.PRINCIPAL, principalId));
        }
        replacePrincipalRoles(principalId, roleIds);
    }

    /**
     * 原子替换 PLATFORM Principal 的平台角色关系。
     *
     * @param principalId PLATFORM Principal 主键
     * @param roleIds 已校验的平台角色主键集合
     * @param version 调用方读取到的 Principal 版本
     */
    public void replacePlatformPrincipalRoles(
            @NotNull Long principalId, @NotNull List<Long> roleIds, @NotNull Long version) {
        if (mapper.advancePlatformPrincipalVersion(principalId, version) != 1) {
            throw new ConcurrencyConflictException(
                    RoleErrorCode.PLATFORM_PRINCIPAL_ROLES_CHANGED, ClientStateTarget.resource(IamResourceTypes.PRINCIPAL, principalId));
        }
        replacePrincipalRoles(principalId, roleIds);
    }

    /**
     * 读取当前绑定指定角色的 Principal，用于角色权限关系变化后的缓存失效。
     *
     * @param roleId 权限关系已变化的角色主键
     * @return 当前绑定该角色且尚未删除的 Principal 所属 Subject 主键
     */
    public @NotNull List<Long> findSubjectIdsAssignedToRole(@NotNull Long roleId) {
        return mapper.selectPrincipalsByRole(roleId).stream()
                .map(IamPrincipalEntity::getSubjectId)
                .distinct()
                .toList();
    }

    /**
     * 创建一个处于 ACTIVE 状态的租户。
     *
     * @param code 已校验的租户编码
     * @param name 已校验的租户名称
     * @param tenantType 租户业务类型
     * @return 已写入数据库的租户主键
     */
    @Transactional
    public @NotNull Long createTenant(
            @NotNull String code, @NotNull String name, @NotNull TenantType tenantType) {
        IamTenantEntity tenant = new IamTenantEntity()
                .setId(idGenerator.nextId())
                .setCode(code)
                .setName(name)
                .setTenantType(tenantType)
                .setStatus(TenantStatus.ACTIVE);
        try {
            mapper.insertTenant(tenant);
            mapper.insertTenantCode(tenant.getId(), code);
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", "uk_iam_tenant_code")
                    || PostgresConstraintViolation.matches(exception, "23505", "pk_iam_tenant_code")) {
                throw new BizException(TenantErrorCode.TENANT_CODE_EXISTS);
            }
            throw exception;
        }
        return tenant.getId();
    }

    /**
     * 创建租户角色并绑定一个代码权限模块下的全部当前有效权限。
     *
     * <p>调用方决定角色业务编码和权限模块；仓储共用生成关系主键并隐藏权限 ID 查询和关系表写入。
     *
     * @param tenantId 角色所属租户
     * @param code 租户内固定角色编码
     * @param name 角色展示名称
     * @param permissionCodePrefix 需要绑定的权限模块编码
     */
    public void createTenantRoleWithPermissions(
            @NotNull Long tenantId,
            @NotNull String code,
            @NotNull String name,
            @NotNull String permissionCodePrefix) {
        IamRoleEntity role = new IamRoleEntity()
                .setId(idGenerator.nextId())
                .setTenantId(tenantId)
                .setCode(code)
                .setName(name)
                .setStatus(RoleStatus.ACTIVE)
                .setBuiltIn(true);
        mapper.insertRole(role);
        QueryWrapper permissionQuery = QueryWrapper.create()
                .select(column(IamPermissionEntity::getId))
                .from(IamPermissionEntity.class)
                .where(IamPermissionEntity::getDeleted)
                .eq(0)
                .and(
                        "(iam_permission.code = ? OR iam_permission.code LIKE ?)",
                        permissionCodePrefix,
                        permissionCodePrefix + ":%")
                .orderBy(IamPermissionEntity::getId)
                .asc();
        Long roleId = role.getId();
        List<IamRolePermissionEntity> relations = mapper.selectObjectListByQueryAs(permissionQuery, Long.class).stream()
                .map(permissionId -> new IamRolePermissionEntity()
                        .setId(idGenerator.nextId())
                        .setRoleId(roleId)
                        .setPermissionId(permissionId))
                .toList();
        if (!relations.isEmpty() && mapper.insertRolePermissions(relations) != relations.size()) {
            throw new IllegalStateException("租户角色权限关系写入行数异常");
        }
    }

    /**
     * 分页读取精确平台或租户范围中的角色，并用一次聚合查询附加整页使用量。
     *
     * <p>非空页固定执行分页 count、分页 records 和一条整页 Principal 聚合，共三条 SQL；空页不执行聚合查询。
     *
     * @param tenantId 精确角色范围；null 表示平台角色
     * @param search 编码或名称搜索；null 表示不筛选
     * @param status 启用、停用或删除状态筛选
     * @param page 页码
     * @param size 每页数量
     * @return 已附加使用量的角色分页
     */
    public @NotNull PageData<ManagedRole> pageRoles(
            @Nullable Long tenantId,
            @Nullable String search,
            @Nullable RoleStatus status,
            long page,
            long size) {
        if (tenantId != null) {
            requireTenantAccess(tenantId, AccessAction.READ);
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(IamRoleEntity::getId).as(ManagedRole::getRoleId),
                        column(IamRoleEntity::getTenantId).as(ManagedRole::getTenantId),
                        column(IamRoleEntity::getCode).as(ManagedRole::getCode),
                        column(IamRoleEntity::getName).as(ManagedRole::getName),
                        column(IamRoleEntity::getStatus).as(ManagedRole::getStatus),
                        column(IamRoleEntity::getBuiltIn).as(ManagedRole::getBuiltIn),
                        column(IamRoleEntity::getCreatedAt).as(ManagedRole::getCreatedAt),
                        column(IamRoleEntity::getUpdatedAt).as(ManagedRole::getUpdatedAt),
                        column(IamRoleEntity::getVersion).as(ManagedRole::getVersion))
                .from(IamRoleEntity.class)
                .where(IamRoleEntity::getDeleted)
                .eq(0)
                .and(IamRoleEntity::getStatus)
                .eq(status, Objects::nonNull);
        if (tenantId == null) {
            query.and(IamRoleEntity::getTenantId).isNull();
        } else {
            query.and(IamRoleEntity::getTenantId).eq(tenantId);
        }
        if (search != null && !search.isBlank()) {
            String keyword = search.trim().toLowerCase(Locale.ROOT);
            query.and(lower(column(IamRoleEntity::getCode))
                    .like(keyword)
                    .or(lower(column(IamRoleEntity::getName)).like(keyword)));
        }
        query.orderBy(IamRoleEntity::getBuiltIn).desc()
                .orderBy(IamRoleEntity::getCreatedAt).desc()
                .orderBy(IamRoleEntity::getId).desc();
        PageData<ManagedRole> roles = PageData.from(mapper.paginateAs(page, size, query, ManagedRole.class));
        if (roles.getRecords().isEmpty()) {
            return roles;
        }
        Map<Long, RoleUsageCount> usageByRole = new LinkedHashMap<>();
        List<Long> roleIds = roles.getRecords().stream().map(ManagedRole::getRoleId).toList();
        for (RoleUsageCount usage : mapper.selectRoleUsageCounts(roleIds)) {
            usageByRole.put(usage.getRoleId(), usage);
        }
        for (ManagedRole role : roles.getRecords()) {
            RoleUsageCount usage = usageByRole.get(role.getRoleId());
            role.setPrincipalCount(usage == null ? 0L : usage.getPrincipalCount());
            role.setPermissionCount(usage == null ? 0L : usage.getPermissionCount());
        }
        return roles;
    }

    /**
     * 读取精确范围中的角色及权限和使用量详情。
     *
     * <p>角色、权限、使用量固定各一条 SQL，共三条，不随权限或 Principal 数量增长。
     *
     * @param tenantId 精确角色范围；null 表示平台角色
     * @param roleId 角色主键
     * @return 角色不存在、已删除或范围不匹配时返回 null
     */
    public @Nullable ManagedRole findManagedRole(@Nullable Long tenantId, @NotNull Long roleId) {
        if (tenantId != null) {
            requireTenantAccess(tenantId, AccessAction.READ);
        }
        QueryWrapper roleQuery = QueryWrapper.create()
                .select(
                        column(IamRoleEntity::getId).as(ManagedRole::getRoleId),
                        column(IamRoleEntity::getTenantId).as(ManagedRole::getTenantId),
                        column(IamRoleEntity::getCode).as(ManagedRole::getCode),
                        column(IamRoleEntity::getName).as(ManagedRole::getName),
                        column(IamRoleEntity::getStatus).as(ManagedRole::getStatus),
                        column(IamRoleEntity::getBuiltIn).as(ManagedRole::getBuiltIn),
                        column(IamRoleEntity::getCreatedAt).as(ManagedRole::getCreatedAt),
                        column(IamRoleEntity::getUpdatedAt).as(ManagedRole::getUpdatedAt),
                        column(IamRoleEntity::getVersion).as(ManagedRole::getVersion))
                .from(IamRoleEntity.class)
                .where(IamRoleEntity::getId)
                .eq(roleId)
                .and(IamRoleEntity::getDeleted)
                .eq(0);
        if (tenantId == null) {
            roleQuery.and(IamRoleEntity::getTenantId).isNull();
        } else {
            roleQuery.and(IamRoleEntity::getTenantId).eq(tenantId);
        }
        ManagedRole role = mapper.selectOneByQueryAs(roleQuery, ManagedRole.class);
        if (role == null) {
            return null;
        }
        QueryWrapper permissionQuery = QueryWrapper.create()
                .select(
                        column(IamPermissionEntity::getId).as(ManagedPermission::getPermissionId),
                        column(IamPermissionEntity::getParentId).as(ManagedPermission::getParentId),
                        column(IamPermissionEntity::getCode).as(ManagedPermission::getCode),
                        column(IamPermissionEntity::getNameKey).as(ManagedPermission::getNameKey),
                        column(IamPermissionEntity::getPermissionType).as(ManagedPermission::getPermissionType),
                        column(IamPermissionEntity::getSortOrder).as(ManagedPermission::getSortOrder))
                .from(IamPermissionEntity.class)
                .innerJoin(IamRolePermissionEntity.class)
                .on(IamRolePermissionEntity::getPermissionId, IamPermissionEntity::getId)
                .where(IamRolePermissionEntity::getRoleId)
                .eq(roleId)
                .and(IamPermissionEntity::getDeleted)
                .eq(0)
                .orderBy(IamPermissionEntity::getSortOrder)
                .asc()
                .orderBy(IamPermissionEntity::getCode)
                .asc();
        role.setPermissions(mapper.selectListByQueryAs(permissionQuery, ManagedPermission.class));
        role.setPermissionCount(role.getPermissions().size());
        role.setPrincipalCount(mapper.countPrincipalsByRole(roleId));
        return role;
    }

    /**
     * 读取一个角色范围内可供分配的代码权限目录。
     *
     * @param tenantScoped 是否限制为 tenant 权限模块
     * @return 按代码目录层级排序的未删除权限
     */
    public @NotNull List<ManagedPermission> findAssignableRolePermissions(boolean tenantScoped) {
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(IamPermissionEntity::getId).as(ManagedPermission::getPermissionId),
                        column(IamPermissionEntity::getParentId).as(ManagedPermission::getParentId),
                        column(IamPermissionEntity::getCode).as(ManagedPermission::getCode),
                        column(IamPermissionEntity::getNameKey).as(ManagedPermission::getNameKey),
                        column(IamPermissionEntity::getPermissionType).as(ManagedPermission::getPermissionType),
                        column(IamPermissionEntity::getSortOrder).as(ManagedPermission::getSortOrder))
                .from(IamPermissionEntity.class)
                .where(IamPermissionEntity::getDeleted)
                .eq(0)
                .and(IamPermissionEntity::getGrantedByDefault)
                .eq(false);
        if (tenantScoped) {
            query.and(column(IamPermissionEntity::getCode)
                    .eq("tenant")
                    .or(column(IamPermissionEntity::getCode).likeLeft("tenant:")));
        }
        query.orderBy(IamPermissionEntity::getSortOrder)
                .asc()
                .orderBy(IamPermissionEntity::getCode)
                .asc();
        return mapper.selectListByQueryAs(query, ManagedPermission.class);
    }

    /** 创建一个权限集合尚为空的自定义角色。 */
    public @NotNull Long createManagedRole(
            @Nullable Long tenantId, @NotNull String code, @NotNull String name) {
        if (tenantId != null) {
            requireTenantAccess(tenantId, AccessAction.MANAGE);
        }
        IamRoleEntity role = new IamRoleEntity()
                .setId(idGenerator.nextId())
                .setTenantId(tenantId)
                .setCode(code)
                .setName(name)
                .setStatus(RoleStatus.ACTIVE)
                .setBuiltIn(false);
        try {
            mapper.insertRole(role);
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", "uk_iam_role_code_tenant")) {
                throw new BizException(RoleErrorCode.ROLE_CODE_EXISTS);
            }
            throw exception;
        }
        return role.getId();
    }

    /** 按精确范围和版本原子更新角色名称及状态。 */
    public void updateManagedRole(
            @Nullable Long tenantId,
            @NotNull Long roleId,
            @NotNull String name,
            @NotNull RoleStatus status,
            long version) {
        if (tenantId != null) {
            requireTenantAccess(tenantId, AccessAction.MANAGE);
        }
        if (mapper.updateRole(roleId, tenantId, name, status, version) != 1) {
            throw new ConcurrencyConflictException(RoleErrorCode.ROLE_CHANGED, ClientStateTarget.resource(IamResourceTypes.ROLE, roleId));
        }
    }

    /**
     * 用已校验权限主键替换角色权限关系。
     *
     * <p>固定执行一次删除，非空集合再执行一次批量插入；调用方事务负责与版本推进共同提交。
     */
    public void replaceManagedRolePermissions(
            @NotNull Long roleId, @NotNull List<Long> permissionIds) {
        mapper.deleteRolePermissions(roleId);
        if (permissionIds.isEmpty()) {
            return;
        }
        List<IamRolePermissionEntity> relations = permissionIds.stream()
                .map(permissionId -> new IamRolePermissionEntity()
                        .setId(idGenerator.nextId())
                        .setRoleId(roleId)
                        .setPermissionId(permissionId))
                .toList();
        if (mapper.insertRolePermissions(relations) != relations.size()) {
            throw new IllegalStateException("角色权限关系写入行数异常");
        }
    }

    /** 按精确范围和版本逻辑删除一个未被 Principal 使用的自定义角色，并清理权限关系。 */
    public void deleteManagedRole(
            @Nullable Long tenantId, @NotNull Long roleId, long version) {
        if (tenantId != null) {
            requireTenantAccess(tenantId, AccessAction.MANAGE);
        }
        if (mapper.deleteCustomRole(roleId, tenantId, version) != 1) {
            throw new ConcurrencyConflictException(RoleErrorCode.ROLE_CHANGED, ClientStateTarget.resource(IamResourceTypes.ROLE, roleId));
        }
        mapper.deleteRolePermissions(roleId);
    }

    /**
     * 分页读取尚未删除的租户。
     *
     * <p>租户编码和名称共同参与搜索，结果按创建时间和主键倒序保持固定翻页顺序。
     *
     * @param search 租户搜索文本
     * @param status 租户当前启用、停用或删除状态
     * @param page 页码
     * @param size 每页记录数
     * @return 租户分页
     */
    public @NotNull PageData<Tenant> pageTenants(
            @Nullable String search, @Nullable TenantStatus status, long page, long size) {
        TenantAccessCriteria criteria = resourceAccess.require(TenantAccessCriteria.class);
        QueryWrapper wrapper = QueryWrapper.create()
                .select(
                        column(IamTenantEntity::getId).as(Tenant::getTenantId),
                        column(IamTenantEntity::getCode).as(Tenant::getCode),
                        column(IamTenantEntity::getName).as(Tenant::getName),
                        column(IamTenantEntity::getTenantType).as(Tenant::getTenantType),
                        column(IamTenantEntity::getStatus).as(Tenant::getStatus),
                        column(IamTenantEntity::getCreatedAt).as(Tenant::getCreatedAt),
                        column(IamTenantEntity::getUpdatedAt).as(Tenant::getUpdatedAt),
                        column(IamTenantEntity::getVersion).as(Tenant::getVersion))
                .from(IamTenantEntity.class)
                .where(IamTenantEntity::getDeleted)
                .eq(0)
                .and(IamTenantEntity::getStatus)
                .eq(status, value -> value != null);
        if (!criteria.isAllData()) {
            if (criteria.getTenantIds().isEmpty()) {
                return PageData.empty(page, size);
            }
            wrapper.and(IamTenantEntity::getId).in(criteria.getTenantIds());
        } else if (!criteria.getExcludedTenantIds().isEmpty()) {
            wrapper.and(IamTenantEntity::getId).notIn(criteria.getExcludedTenantIds());
        }
        if (search != null && !search.isBlank()) {
            // 租户列表按编码和名称执行不区分大小写的包含搜索，两列使用同一个去除首尾空白的关键词。
            String pattern = "%" + search + "%";
            wrapper.and("(iam_tenant.code ILIKE ? OR iam_tenant.name ILIKE ?)", pattern, pattern);
        }
        wrapper.orderBy(IamTenantEntity::getCreatedAt).desc()
                .orderBy(IamTenantEntity::getId).desc();
        return PageData.from(mapper.paginateAs(page, size, wrapper, Tenant.class));
    }

    /**
     * 读取租户展示对象。
     *
     * @param tenantId 租户主键
     * @return 租户；不存在时为 {@code null}
     */
    public @Nullable Tenant findTenant(@NotNull Long tenantId) {
        requireTenantAccess(tenantId, AccessAction.READ);
        QueryWrapper wrapper = QueryWrapper.create()
                .select(
                        column(IamTenantEntity::getId).as(Tenant::getTenantId),
                        column(IamTenantEntity::getCode).as(Tenant::getCode),
                        column(IamTenantEntity::getName).as(Tenant::getName),
                        column(IamTenantEntity::getTenantType).as(Tenant::getTenantType),
                        column(IamTenantEntity::getStatus).as(Tenant::getStatus),
                        column(IamTenantEntity::getCreatedAt).as(Tenant::getCreatedAt),
                        column(IamTenantEntity::getUpdatedAt).as(Tenant::getUpdatedAt),
                        column(IamTenantEntity::getVersion).as(Tenant::getVersion))
                .from(IamTenantEntity.class)
                .where(IamTenantEntity::getId)
                .eq(tenantId)
                .and(IamTenantEntity::getDeleted)
                .eq(0);
        return mapper.selectOneByQueryAs(wrapper, Tenant.class);
    }

    /**
     * 按当前业务编码读取匿名认证入口使用的租户，不应用管理端数据范围。
     *
     * @param tenantCode 当前租户业务编码
     * @return 当前编码对应的租户；不存在时为 {@code null}
     */
    public @Nullable Tenant findTenantByCode(@NotNull String tenantCode) {
        QueryWrapper wrapper = QueryWrapper.create()
                .select(
                        column(IamTenantEntity::getId).as(Tenant::getTenantId),
                        column(IamTenantEntity::getCode).as(Tenant::getCode),
                        column(IamTenantEntity::getName).as(Tenant::getName),
                        column(IamTenantEntity::getTenantType).as(Tenant::getTenantType),
                        column(IamTenantEntity::getStatus).as(Tenant::getStatus),
                        column(IamTenantEntity::getCreatedAt).as(Tenant::getCreatedAt),
                        column(IamTenantEntity::getUpdatedAt).as(Tenant::getUpdatedAt),
                        column(IamTenantEntity::getVersion).as(Tenant::getVersion))
                .from(IamTenantEntity.class)
                .where(IamTenantEntity::getCode)
                .eq(tenantCode)
                .and(IamTenantEntity::getDeleted)
                .eq(0);
        return mapper.selectOneByQueryAs(wrapper, Tenant.class);
    }

    /**
     * 按当前资料契约原子更新租户资源。
     *
     * <p>固定执行一次原子资料更新；编码变化时再执行一次历史编码登记。历史编码冲突和更新结果处于同一事务，任一步失败都会回滚。
     *
     * @param tenantId 租户主键
     * @param code 当前请求提供且已校验的租户编码
     * @param name 当前请求提供且已校验的租户名称
     * @param version 调用方读取到的乐观锁版本
     * @throws ConcurrencyConflictException 租户已删除或版本已变化时抛出
     */
    @Transactional
    public void updateTenant(
            @NotNull Long tenantId,
            @NotNull String code,
            @NotNull String name,
            @NotNull Long version) {
        requireTenantAccess(tenantId, AccessAction.WRITE);
        try {
            String previousCode = mapper.updateTenant(tenantId, code, name, version);
            if (previousCode == null) {
                throw new ConcurrencyConflictException(TenantErrorCode.TENANT_CHANGED, ClientStateTarget.resource(IamResourceTypes.TENANT, tenantId));
            }
            if (!previousCode.equals(code)) {
                mapper.insertTenantCode(tenantId, code);
            }
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", "uk_iam_tenant_code")
                    || PostgresConstraintViolation.matches(exception, "23505", "pk_iam_tenant_code")) {
                throw new BizException(TenantErrorCode.TENANT_CODE_EXISTS);
            }
            throw exception;
        }
    }

    /**
     * 原子更新租户状态。
     *
     * @param tenantId 租户主键
     * @param status 目标状态
     * @param version 调用方读取到的乐观锁版本
     * @throws ConcurrencyConflictException 租户已删除或版本已变化时抛出
     */
    public void updateTenantStatus(
            @NotNull Long tenantId, @NotNull TenantStatus status, @NotNull Long version) {
        requireTenantAccess(tenantId, AccessAction.WRITE);
        if (mapper.updateTenantStatus(tenantId, status, version) == 0) {
            throw new ConcurrencyConflictException(TenantErrorCode.TENANT_CHANGED, ClientStateTarget.resource(IamResourceTypes.TENANT, tenantId));
        }
    }

    /**
     * 锁定仍存在的租户，使归属资源写入与租户终态删除在集群中串行。
     *
     * <p>调用方事务持有 PostgreSQL 行级 SHARE 锁到提交或回滚。删除先取得 NO KEY UPDATE 锁；两者竞争时，
     * 后取得锁的一方会在最新提交快照上重新判断租户是否仍存在。
     *
     * @param tenantId 即将写入归属资源的租户主键
     * @return 锁定时的租户状态
     * @throws BizException 租户不存在或已经删除时抛出
     */
    public @NotNull TenantStatus lockExistingTenantForWrite(@NotNull Long tenantId) {
        requireTenantAccess(tenantId, AccessAction.WRITE);
        TenantStatus status = mapper.selectTenantStatusForShare(tenantId);
        if (status == null) {
            throw new BizException(TenantErrorCode.TENANT_NOT_FOUND);
        }
        return status;
    }

    /**
     * 同步隔离租户、成员 Principal 和租户角色，并登记异步清理任务。
     *
     * <p>固定执行 5 条 SQL：租户终态更新、Subject 集合读取、Principal 隔离、角色隔离和任务登记。返回集合
     * 供事务提交后一次批量刷新 Session，不从已经隔离的 Principal 反查。
     *
     * @param tenantId 待最终删除的租户主键
     * @param version 删除请求读取到的租户版本
     * @return 删除前属于该租户的去重 Subject 主键
     * @throws ConcurrencyConflictException 租户已删除或版本已变化时抛出
     */
    public @NotNull List<Long> isolateDeletedTenant(
            @NotNull Long tenantId, @NotNull Long version) {
        requireTenantAccess(tenantId, AccessAction.MANAGE);
        if (mapper.isolateTenant(tenantId, version) != 1) {
            throw new ConcurrencyConflictException(TenantErrorCode.TENANT_CHANGED, ClientStateTarget.resource(IamResourceTypes.TENANT, tenantId));
        }
        List<Long> subjectIds = mapper.selectTenantSubjectIds(tenantId);
        mapper.isolateTenantMemberships(tenantId);
        mapper.isolateTenantPrincipals(tenantId);
        mapper.isolateTenantRoles(tenantId);
        mapper.insertTenantCleanupTask(idGenerator.nextId(), tenantId);
        return subjectIds;
    }

    /**
     * 确认当前请求可以新增租户。
     *
     * <p>新租户尚不存在，限定范围无法表达其归属，因此只有全部租户范围才能创建。该校验仅由管理业务操作调用；
     * Bootstrap 等系统初始化直接写入租户及其授权记录，不经过 HTTP 访问范围检查。
     */
    public void requireTenantCreationAccess() {
        TenantAccessCriteria criteria = resourceAccess.require(TenantAccessCriteria.class);
        if (!criteria.getAction().permits(AccessAction.WRITE) || !criteria.isAllData()) {
            throw new ResourceAccessDeniedException();
        }
    }

    /**
     * 确认当前请求可以在指定租户范围内执行 Repository 操作。
     *
     * @param tenantId 即将读写的固定租户归属
     * @param requiredAction 当前持久化操作要求的最低访问等级
     */
    private void requireTenantAccess(@NotNull Long tenantId, @NotNull AccessAction requiredAction) {
        TenantAccessCriteria criteria = resourceAccess.require(TenantAccessCriteria.class);
        if (!criteria.getAction().permits(requiredAction)
                || (criteria.getTargetTenantId() != null && !criteria.getTargetTenantId().equals(tenantId))
                || (criteria.isAllData()
                        ? criteria.getExcludedTenantIds().contains(tenantId)
                        : !criteria.getTenantIds().contains(tenantId))) {
            throw new ResourceAccessDeniedException();
        }
    }

    /**
     * 读取 Subject 已加入的租户成员关系。
     *
     * @param subjectId 登录身份主键
     * @return 按租户名称排序的成员关系
     */
    public @NotNull List<TenantMembership> findTenantMemberships(@NotNull Long subjectId) {
        return mapper.selectTenantMemberships(subjectId);
    }

    /**
     * 批量读取一组 Subject 当前由已注册 Scheme 支持的安全 Credential 字段。
     *
     * <p>固定执行一条查询，只选择管理展示所需列，密码摘要和盐不会进入返回结果。
     *
     * @param subjectIds 需要读取凭证的 Subject 主键集合
     * @param credentialTypes 当前应用已注册 Scheme 对应的持久化类型
     * @return 以 Subject 主键索引的安全凭证集合；每个 Credential 保留直接所属 Account 主键
     */
    public @NotNull Map<Long, List<IamCredentialEntity>> findCredentials(
            @NotNull List<Long> subjectIds, @NotNull Set<CredentialType> credentialTypes) {
        if (subjectIds.isEmpty() || credentialTypes.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<IamCredentialEntity>> bySubject = new LinkedHashMap<>();
        for (Long subjectId : subjectIds) {
            bySubject.putIfAbsent(subjectId, new ArrayList<>());
        }
        mapper.selectCredentialRowsBySubjectIds(subjectIds, credentialTypes).forEach(row -> {
            List<IamCredentialEntity> credentials = bySubject.get(row.getSubjectId());
            if (credentials != null) {
                credentials.add(new IamCredentialEntity()
                        .setId(row.getCredentialId())
                        .setAccountId(row.getAccountId())
                        .setCredentialType(row.getCredentialType())
                        .setStatus(row.getStatus())
                        .setCreatedAt(row.getCreatedAt())
                        .setUpdatedAt(row.getUpdatedAt())
                        .setVersion(row.getVersion()));
            }
        });
        Map<Long, List<IamCredentialEntity>> immutable = new LinkedHashMap<>();
        bySubject.forEach((subjectId, credentials) -> immutable.put(subjectId, List.copyOf(credentials)));
        return Collections.unmodifiableMap(immutable);
    }

    /**
     * 确认完成认证的 Credential 仍保持相同 Account、版本和有效状态。
     *
     * @param credentialId 完成本次认证的 Credential 主键
     * @param accountId 验证结果中的 Account 主键
     * @param version 完成验证时的 Credential 版本
     * @return 验证数据仍是当前值时返回 true
     */
    public boolean isCredentialCurrent(
            @NotNull Long credentialId, @NotNull Long accountId, @NotNull Long version) {
        QueryWrapper wrapper = QueryWrapper.create()
                .select(column(IamCredentialEntity::getId))
                .from(IamCredentialEntity.class)
                .where(IamCredentialEntity::getId)
                .eq(credentialId)
                .and(IamCredentialEntity::getAccountId)
                .eq(accountId)
                .and(IamCredentialEntity::getVersion)
                .eq(version)
                .and(IamCredentialEntity::getStatus)
                .eq(CredentialStatus.ACTIVE)
                .and(IamCredentialEntity::getDeleted)
                .eq(0);
        return mapper.selectCountByQuery(wrapper) == 1;
    }

    /**
     * 原子变更一个当前 Credential 的状态。
     *
     * @param credentialId Credential 主键
     * @param accountId 所属 Account 主键
     * @param status 目标状态
     * @param version 调用方读取到的 Credential 版本
     * @throws ConcurrencyConflictException 凭证不存在、已删除、归属不匹配或版本变化时抛出
     */
    public void updateCredentialStatus(
            @NotNull Long credentialId,
            @NotNull Long accountId,
            @NotNull CredentialStatus status,
            @NotNull Long version) {
        if (mapper.updateCredentialStatus(credentialId, accountId, status, version) == 0) {
            throw new ConcurrencyConflictException(IamErrorCode.CREDENTIAL_CHANGED, ClientStateTarget.resource(IamResourceTypes.CREDENTIAL, credentialId));
        }
    }

    /**
     * 原子逻辑删除一个当前 Credential。
     *
     * @param credentialId Credential 主键
     * @param accountId 所属 Account 主键
     * @param version 调用方读取到的 Credential 版本
     * @throws ConcurrencyConflictException 凭证不存在、已删除、归属不匹配或版本变化时抛出
     */
    public void deleteCredential(
            @NotNull Long credentialId, @NotNull Long accountId, @NotNull Long version) {
        if (mapper.deleteCredential(credentialId, accountId, version) == 0) {
            throw new ConcurrencyConflictException(IamErrorCode.CREDENTIAL_CHANGED, ClientStateTarget.resource(IamResourceTypes.CREDENTIAL, credentialId));
        }
    }

    /**
     * 读取一个 Account 当前未删除的安全 Credential 字段。
     *
     * <p>固定执行一条单表 SQL，返回管理操作需要的版本、状态和类型，不返回摘要与盐。
     *
     * @param accountId Account 主键
     * @return 按类型和 Credential 主键排序的安全凭证
     */
    public @NotNull List<IamCredentialEntity> findCredentialsByAccount(@NotNull Long accountId) {
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(IamCredentialEntity::getId),
                        column(IamCredentialEntity::getAccountId),
                        column(IamCredentialEntity::getCredentialType),
                        column(IamCredentialEntity::getStatus),
                        column(IamCredentialEntity::getCreatedAt),
                        column(IamCredentialEntity::getUpdatedAt),
                        column(IamCredentialEntity::getVersion))
                .from(IamCredentialEntity.class)
                .where(IamCredentialEntity::getAccountId)
                .eq(accountId)
                .and(IamCredentialEntity::getDeleted)
                .eq(0)
                .orderBy(IamCredentialEntity::getCredentialType)
                .asc()
                .orderBy(IamCredentialEntity::getId)
                .asc();
        return mapper.selectListByQueryAs(query, IamCredentialEntity.class).stream()
                .map(stored -> new IamCredentialEntity()
                        .setId(stored.getId())
                        .setAccountId(stored.getAccountId())
                        .setCredentialType(stored.getCredentialType())
                        .setStatus(stored.getStatus())
                        .setCreatedAt(stored.getCreatedAt())
                        .setUpdatedAt(stored.getUpdatedAt())
                        .setVersion(stored.getVersion()))
                .toList();
    }

    /**
     * 将代码权限目录同步为 IAM 权限数据，并逻辑删除已从目录移除的权限。
     *
     * <p>目录按父节点先于子节点的顺序展开。仓储在单次同步中维护代码到数据库主键的映射，使父子关系只依赖
     * 固定的权限编码，不把数据库主键泄漏给目录或调用方。
     *
     * @param catalog 当前进程加载完成的代码权限目录
     */
    public void synchronizePermissionCatalog(@NotNull PermissionCatalog catalog) {
        List<String> codes = catalog.items().stream().map(PermissionItem::getCode).toList();
        Map<String, IamPermissionEntity> existingByCode = mapper.selectPermissionsIncludingDeleted(codes).stream()
                .collect(Collectors.toMap(
                        IamPermissionEntity::getCode,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, Long> permissionIds = new LinkedHashMap<>();
        for (PermissionItem item : catalog.items()) {
            IamPermissionEntity existing = existingByCode.get(item.getCode());
            permissionIds.put(item.getCode(), existing == null ? idGenerator.nextId() : existing.getId());
        }
        List<IamPermissionEntity> permissions = catalog.items().stream().map(item -> {
            Long parentId = item.getParentCode() == null ? null : permissionIds.get(item.getParentCode());
            if (item.getParentCode() != null && parentId == null) {
                throw new IllegalStateException("权限目录缺少父节点: " + item.getParentCode());
            }
            return new IamPermissionEntity()
                    .setId(permissionIds.get(item.getCode()))
                    .setParentId(parentId)
                    .setCode(item.getCode())
                    .setNameKey(item.getNameKey())
                    .setPermissionType(item.getType())
                    .setSortOrder(item.getSortOrder());
        }).toList();
        if (!permissions.isEmpty() && mapper.upsertPermissions(permissions) != permissions.size()) {
            throw new IllegalStateException("权限目录写入行数异常");
        }
        mapper.deletePermissionsExcept(codes);
    }

    /**
     * 读取 IAM Seed 已维护的内置平台管理员角色。
     *
     * @return 当前未删除的平台管理员角色主键
     * @throws IllegalStateException Seed 未创建平台管理员角色时抛出
     */
    public @NotNull Long requirePlatformAdministratorRoleId() {
        QueryWrapper roleQuery = QueryWrapper.create()
                .from(IamRoleEntity.class)
                .where(IamRoleEntity::getTenantId)
                .isNull()
                .and(IamRoleEntity::getCode)
                .eq("PLATFORM_ADMIN")
                .and(IamRoleEntity::getDeleted)
                .eq(0);
        IamRoleEntity role = mapper.selectOneByQueryAs(roleQuery, IamRoleEntity.class);
        if (role == null) {
            throw new IllegalStateException("IAM Seed 未创建 PLATFORM_ADMIN 角色");
        }
        return role.getId();
    }

}
