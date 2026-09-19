package com.sca4d.iam.base;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iam.base.mapper.PlatformAccountMapper;
import com.sca4d.iam.identity.IamErrorCode;
import com.sca4d.iam.identity.IdentityStatus;
import com.sca4d.iam.identity.PrincipalType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 隔离 Platform Account 目录服务对 MyBatis 查询和影响行数的依赖。 */
@ApiStatus.Internal
public class PlatformAccountDirectoryRepository {
    private final @NotNull PlatformAccountMapper mapper;

    private final @NotNull PrincipalType principalType;

    private final @NotNull SqlBatches sqlBatches;

    /**
     * 创建固定查询 PlatformPrincipal 的账号目录 Repository。
     *
     * @param mapper Platform Account Mapper
     * @param principalType PlatformPrincipal 的持久化类型
     * @param sqlBatches SQL 批处理器
     */
    public PlatformAccountDirectoryRepository(
            @NotNull PlatformAccountMapper mapper,
            @NotNull PrincipalType principalType,
            @NotNull SqlBatches sqlBatches) {
        this.mapper = Objects.requireNonNull(mapper, "Platform Account Mapper 不能为空");
        this.principalType = Objects.requireNonNull(principalType, "PlatformPrincipal 类型不能为空");
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
    }

    /** @return 当前页账号和完整命中数量 */
    @NotNull
    Page<PlatformAccountEntry> find(@NotNull PlatformAccountQuery query) {
        PlatformAccountQuery checked = Objects.requireNonNull(query, "Platform Account 查询不能为空");
        long total = mapper.countPlatformAccounts(principalType, checked.search(), checked.status(), null, false);
        List<PlatformAccountEntry> items = total == 0
                ? List.of()
                : List.copyOf(
                        mapper.selectPlatformAccounts(
                                principalType,
                                checked.search(),
                                checked.status(),
                                null,
                                false,
                                checked.offset(),
                                checked.page().size()));
        return new Page<>(items, checked.page(), total);
    }

    /**
     * 查询指定角色已分配或未分配的一页 Platform Account。
     *
     * @param roleId Platform 角色标识
     * @param assigned 查询已分配 Account 时为 {@code true}
     * @param query 分页、用户名和状态筛选
     * @return 当前页 Account 和完整命中数量
     */
    @CheckReturnValue
    @NotNull
    public Page<PlatformAccountEntry> findRoleAccounts(
            @NotNull Long roleId,
            boolean assigned,
            @NotNull PlatformAccountQuery query) {
        Long checkedRoleId = Objects.requireNonNull(roleId, "角色标识不能为空");
        PlatformAccountQuery checked = Objects.requireNonNull(query, "Platform Account 查询不能为空");
        long total = mapper.countPlatformAccounts(
                principalType,
                checked.search(),
                checked.status(),
                checkedRoleId,
                assigned);
        List<PlatformAccountEntry> items = total == 0
                ? List.of()
                : List.copyOf(
                        mapper.selectPlatformAccounts(
                                principalType,
                                checked.search(),
                                checked.status(),
                                checkedRoleId,
                                assigned,
                                checked.offset(),
                                checked.page().size()));
        return new Page<>(items, checked.page(), total);
    }

    /**
     * 批量读取 Platform Account，并拒绝缺失或关联多个 PlatformPrincipal 的结果。
     *
     * @param accountIds 不包含空值或重复值的 Account 标识
     * @return 按 Account 标识索引的不可修改目录项
     */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    public Map<@NotNull Long, @NotNull PlatformAccountEntry> loadByIds(
            @NotNull Collection<@NotNull Long> accountIds) {
        // 先校验完整输入，避免分批读取后才发现重复标识并产生不完整结果。
        LinkedHashSet<Long> unique = new LinkedHashSet<>(
                Objects.requireNonNull(accountIds, "Account 标识集合不能为空"));
        if (unique.contains(null) || unique.size() != accountIds.size()) {
            throw new IllegalArgumentException("Account 标识不能包含空值或重复值");
        }
        if (unique.isEmpty()) {
            return Map.of();
        }

        // 按输入顺序分批查询，并确保每个 Account 只对应一个 PlatformPrincipal。
        LinkedHashMap<Long, PlatformAccountEntry> result = new LinkedHashMap<>();
        List<Long> ordered = List.copyOf(unique);
        for (PlatformAccountEntry account : sqlBatches.query(
                ordered,
                1,
                1,
                batch -> mapper.selectPlatformAccountsByIds(batch, principalType))) {
            if (result.putIfAbsent(account.accountId(), account) != null) {
                throw new IllegalStateException("Platform Account 关联了多个 PlatformPrincipal");
            }
        }
        if (!result.keySet().equals(unique)) {
            throw new BusinessException(IamErrorCode.ACCOUNT_UNAVAILABLE);
        }
        return Map.copyOf(result);
    }

    /** 修改账号状态。 */
    @AffectedRows(target = "Platform Account 状态")
    void updateStatus(@NotNull Long accountId, @NotNull IdentityStatus status) {
        mapper.updatePlatformAccountStatus(accountId, status);
    }

}
