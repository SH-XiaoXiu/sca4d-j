package com.sca4d.iam.identity;

import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.persistence.exception.AffectedRowsMismatchException;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iam.identity.mapper.IdentityMapper;
import com.sca4d.iam.identity.mapper.PrincipalAccountLink;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** {@link IdentityService} 使用的包内持久化边界。 */
@ApiStatus.Internal
public class IdentityRepository {
    private final @NotNull IdentityMapper mapper;

    private final @NotNull SqlBatches sqlBatches;

    /** 创建使用统一 SQL 批次限制的 IAM 数据读写入口。 */
    public IdentityRepository(@NotNull IdentityMapper mapper, @NotNull SqlBatches sqlBatches) {
        this.mapper = Objects.requireNonNull(mapper, "身份 Mapper 不能为空");
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
    }

    /** 按调用方确定的初始状态创建 `Subject`。 */
    void insertSubject(@NotNull Long id, @NotNull SubjectType type, @NotNull IdentityStatus status) {
        AffectedRowCounts.requireSingle(
                mapper.insertSubjects(List.of(new Subject(id, type)), status),
                ZeroRowPolicy.ERROR,
                "创建 Subject");
    }

    /** 按 ID 读取 `Subject`，包括停用状态。 */
    @CheckReturnValue
    @NotNull
    Optional<Subject> findSubject(@NotNull Long id) {
        return Optional.ofNullable(mapper.selectSubject(id, null));
    }

    /** @return 按标识索引的未删除 Subject；缺失标识不包含在结果中 */
    Map<Long, Subject> findSubjects(Collection<Long> ids) {
        Map<Long, Subject> result = new LinkedHashMap<>();
        for (Subject subject : sqlBatches.query(new LinkedHashSet<>(ids), 1, 0, mapper::selectSubjects)) {
            result.put(subject.subjectId(), subject);
        }
        return Map.copyOf(result);
    }

    /** 读取 `Subject` 当前状态。 */
    @CheckReturnValue
    @NotNull
    Optional<IdentityStatus> findSubjectStatus(@NotNull Long id) {
        return Optional.ofNullable(mapper.selectSubjectStatus(id));
    }

    /** 按 ID 读取启用的 `Subject`。 */
    @CheckReturnValue
    @NotNull
    Optional<Subject> findActiveSubject(@NotNull Long id) {
        return Optional.ofNullable(mapper.selectSubject(id, IdentityStatus.ACTIVE));
    }

    /** 按 ID 修改 `Subject` 状态。 */
    @CheckReturnValue
    boolean updateSubjectStatus(@NotNull Long id, @NotNull IdentityStatus status) {
        return AffectedRowCounts.changed(mapper.updateSubjectStatus(id, status), "修改 Subject 状态");
    }

    /** 按指定初始状态创建 `Account`。 */
    void insertAccount(
            @NotNull Long id,
            @NotNull Long subjectId,
            @NotNull RealmKey realm,
            @NotNull IdentityStatus status) {
        AffectedRowCounts.requireSingle(
                mapper.insertAccount(id, subjectId, realm, status),
                ZeroRowPolicy.ERROR,
                "创建 Account");
    }

    /** 按 ID 读取 `Account`，包括停用状态。 */
    @CheckReturnValue
    @NotNull
    Optional<Account> findAccount(@NotNull Long id) {
        return mapper.selectAccounts(id, null, null, null, null).stream().findFirst();
    }

    /** 读取账号当前状态。 */
    @CheckReturnValue
    @NotNull
    Optional<IdentityStatus> findAccountStatus(@NotNull Long id) {
        return Optional.ofNullable(mapper.selectAccountStatus(id));
    }

    /** 按 ID 读取启用的 `Account`。 */
    @CheckReturnValue
    @NotNull
    Optional<Account> findActiveAccount(@NotNull Long id) {
        return mapper.selectAccounts(id, null, null, IdentityStatus.ACTIVE, IdentityStatus.ACTIVE).stream().findFirst();
    }

    /** 读取 `Subject` 拥有的全部 `Account`。 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull Account> findAccounts(@NotNull Long subjectId) {
        return List.copyOf(mapper.selectAccounts(null, subjectId, null, null, null));
    }

    /** 按 Subject 标识批量读取 Account。 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    Map<@NotNull Long, @NotNull List<@NotNull Account>> findAccounts(
            @NotNull Collection<@NotNull Long> subjectIds) {
        LinkedHashMap<Long, List<Account>> result = new LinkedHashMap<>();
        LinkedHashSet<Long> selected = new LinkedHashSet<>(Objects.requireNonNull(subjectIds, "Subject 标识集合不能为空"));
        if (selected.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("Subject 标识不能为空");
        }
        selected.forEach(id -> result.put(id, new ArrayList<>()));
        if (selected.isEmpty()) {
            return Map.of();
        }
        for (Account account : sqlBatches.query(selected, 1, 0, mapper::selectAccountsBySubjectIds)) {
            Objects.requireNonNull(result.get(account.subjectId()), "Account 不属于请求的 Subject").add(account);
        }
        result.replaceAll((id, accounts) -> List.copyOf(accounts));
        return Collections.unmodifiableMap(result);
    }

    /** 按标识修改账号状态。 */
    @CheckReturnValue
    boolean updateAccountStatus(@NotNull Long id, @NotNull IdentityStatus status) {
        return AffectedRowCounts.changed(mapper.updateAccountStatus(id, status), "修改账号状态");
    }

    /** 按调用方确定的初始状态创建 `Principal`。 */
    void insertPrincipal(@NotNull Long id, @NotNull PrincipalType type, @NotNull IdentityStatus status) {
        AffectedRowCounts.requireSingle(
                mapper.insertPrincipal(id, type, status),
                ZeroRowPolicy.ERROR,
                "创建 Principal");
    }

    /** 按 ID 和类型读取 `Principal`，包括停用状态。 */
    @CheckReturnValue
    @NotNull
    Optional<Long> findPrincipalId(@NotNull Long id, @NotNull PrincipalType type) {
        return findPrincipalIds(List.of(id), type, null, null).stream().findFirst();
    }

    /** 按标识和类型读取业务主体当前状态。 */
    @CheckReturnValue
    @NotNull
    Optional<IdentityStatus> findPrincipalStatus(@NotNull Long id, @NotNull PrincipalType type) {
        return Optional.ofNullable(mapper.selectPrincipalStatus(id, type));
    }

    /** 按 ID 和类型读取启用的 `Principal`。 */
    @CheckReturnValue
    @NotNull
    Optional<Long> findActivePrincipalId(@NotNull Long id, @NotNull PrincipalType type) {
        return findPrincipalIds(List.of(id), type, IdentityStatus.ACTIVE, null).stream().findFirst();
    }

    /** 判断任意类型的业务主体是否启用。 */
    @CheckReturnValue
    boolean isActivePrincipal(@NotNull Long id) {
        return !findPrincipalIds(List.of(id), null, IdentityStatus.ACTIVE, null).isEmpty();
    }

    /** 按可选类型、状态和 Account 关系分批筛选 Principal 标识。 */
    @CheckReturnValue
    @NotNull
    Set<Long> findPrincipalIds(
            @NotNull Collection<Long> principalIds,
            @Nullable PrincipalType type,
            @Nullable IdentityStatus status,
            @Nullable LinkedAccountCondition linkedAccount) {
        List<Long> selected = List.copyOf(principalIds);
        RealmKey linkedAccountRealm = linkedAccount == null ? null : linkedAccount.realm();
        Set<Long> matched = new LinkedHashSet<>(
                sqlBatches.query(
                        selected,
                        1,
                        3,
                        batch -> mapper.selectPrincipalIds(batch, type, status, linkedAccountRealm)));
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        for (Long principalId : selected) {
            if (matched.contains(principalId)) {
                ordered.add(principalId);
            }
        }
        return Collections.unmodifiableSet(ordered);
    }

    /** 读取 `Account` 关联的指定类型 `Principal`。 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull Long> findActiveLinkedPrincipalIds(
            @NotNull Long accountId,
            @NotNull PrincipalType type) {
        return List.copyOf(mapper.selectLinkedPrincipalIds(accountId, type, IdentityStatus.ACTIVE));
    }

    /** 按标识修改指定类型的业务主体状态。 */
    @CheckReturnValue
    boolean updatePrincipalStatus(@NotNull Long id, @NotNull IdentityStatus status) {
        return AffectedRowCounts.changed(
                mapper.updatePrincipals(List.of(id), null, false, Patch.set(status), Patch.ignore()),
                "修改业务主体状态");
    }

    /**
     * 批量逻辑删除指定类型且尚未删除的 Principal。
     *
     * @param ids Principal 标识集合；调用方必须已经去重
     * @param type Principal 类型
     * @throws AffectedRowsMismatchException 实际删除数量与目标数量不一致时抛出
     */
    void deletePrincipals(
            @NotNull Collection<@NotNull Long> ids,
            @NotNull PrincipalType type) {
        sqlBatches.forEach(ids, 1, 4, batch -> {
            AffectedRowCounts.requireExactly(
                    mapper.updatePrincipals(batch, type, false, Patch.set(IdentityStatus.DISABLED), Patch.set(true)),
                    batch.size(),
                    "批量删除业务主体");
        });
    }

    /**
     * 批量恢复指定类型且已经逻辑删除的 Principal。
     *
     * @param ids Principal 标识集合；调用方必须已经去重
     * @param type Principal 类型
     * @throws AffectedRowsMismatchException 实际恢复数量与目标数量不一致时抛出
     */
    void restorePrincipals(
            @NotNull Collection<@NotNull Long> ids,
            @NotNull PrincipalType type) {
        sqlBatches.forEach(ids, 1, 4, batch -> {
            AffectedRowCounts.requireExactly(
                    mapper.updatePrincipals(batch, type, true, Patch.set(IdentityStatus.ACTIVE), Patch.set(false)),
                    batch.size(),
                    "批量恢复业务主体");
        });
    }

    /** 幂等新增账号与业务主体关系。 */
    void link(@NotNull Long accountId, @NotNull Long principalId) {
        AffectedRowCounts.requireSingle(
                mapper.insertLink(accountId, principalId),
                ZeroRowPolicy.ALLOW,
                "新增账号与业务主体关系");
    }

    /** 删除账号与业务主体关系。 */
    @CheckReturnValue
    boolean unlink(@NotNull Long accountId, @NotNull Long principalId) {
        return AffectedRowCounts.changed(mapper.deleteLink(accountId, principalId), "删除账号与业务主体关系");
    }

    /** 读取账号关联的全部或指定类型业务主体标识。 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull Long> findLinkedPrincipalIds(
            @NotNull Long accountId,
            @Nullable PrincipalType type) {
        return List.copyOf(mapper.selectLinkedPrincipalIds(accountId, type, null));
    }

    /** 读取业务主体关联的账号标识。 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull Long> findLinkedAccountIds(@NotNull Long principalId) {
        return findLinkedAccountIds(List.of(principalId)).get(principalId);
    }

    /** 读取 `Principal` 关联的启用 `Account`。 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull Account> findActiveLinkedAccounts(@NotNull Long principalId) {
        return List.copyOf(mapper.selectAccounts(null, null, principalId, IdentityStatus.ACTIVE, null));
    }

    /** 批量读取业务主体与账号关系。 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    Map<@NotNull Long, @NotNull List<@NotNull Long>> findLinkedAccountIds(
            @NotNull Collection<@NotNull Long> ids) {
        LinkedHashMap<Long, List<Long>> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, new ArrayList<>()));
        List<Long> selected = List.copyOf(new LinkedHashSet<>(ids));
        for (PrincipalAccountLink link : sqlBatches.query(selected, 1, 0, mapper::selectPrincipalAccountLinks)) {
            result.get(link.principalId()).add(link.accountId());
        }
        result.replaceAll((id, accountIds) -> List.copyOf(accountIds));
        return Collections.unmodifiableMap(result);
    }

    /** 创建 `AccountIdentifier` 登录标识。 */
    void insertIdentifier(@NotNull AccountIdentifier identifier) {
        int inserted = mapper.insertIdentifier(
                identifier.id(),
                identifier.accountId(),
                identifier.realmKey(),
                identifier.type(),
                identifier.value(),
                identifier.normalizedValue(),
                identifier.status());
        AffectedRowCounts.requireSingle(inserted, ZeroRowPolicy.ERROR, "新增登录标识");
    }

    /** 按主键读取登录标识。 */
    @CheckReturnValue
    @NotNull
    Optional<AccountIdentifier> findIdentifier(long id) {
        return mapper.selectIdentifiers(id, null).stream().findFirst();
    }

    /** 读取账号拥有的全部登录标识。 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull AccountIdentifier> findIdentifiers(@NotNull Long accountId) {
        return List.copyOf(mapper.selectIdentifiers(null, accountId));
    }

    /** 更新登录标识并直接使用数据库返回值，避免另一次查询。 */
    @NotNull
    Optional<AccountIdentifier> changeIdentifierValue(long id, String value, String normalizedValue) {
        return Optional.ofNullable(
                mapper.updateIdentifier(
                        id,
                        Patch.set(value),
                        Patch.set(normalizedValue),
                        Patch.ignore(),
                        Patch.ignore()));
    }

    /** 修改登录标识状态。 */
    @CheckReturnValue
    boolean updateIdentifierStatus(long id, @NotNull IdentityStatus status) {
        return mapper.updateIdentifier(id, Patch.ignore(), Patch.ignore(), Patch.set(status), Patch.ignore()) != null;
    }

    /** 逻辑删除登录标识。 */
    @CheckReturnValue
    boolean deleteIdentifier(long id) {
        return mapper.updateIdentifier(
                id,
                Patch.ignore(),
                Patch.ignore(),
                Patch.set(IdentityStatus.DISABLED),
                Patch.set(true)) != null;
    }

    /** 用规范化登录标识查找可用的 `Account`，同时检查 `Subject` 和登录标识状态。 */
    @CheckReturnValue
    @NotNull
    Optional<Long> resolveActiveAccountId(
            @NotNull RealmKey realm,
            @NotNull AccountIdentifierType type,
            @NotNull String normalized) {
        return Optional.ofNullable(mapper.resolveActiveAccountId(realm, type, normalized));
    }
}
