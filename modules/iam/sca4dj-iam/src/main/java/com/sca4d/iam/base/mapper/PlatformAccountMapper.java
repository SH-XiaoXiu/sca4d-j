package com.sca4d.iam.base.mapper;

import com.sca4d.iam.base.PlatformAccountEntry;
import com.sca4d.iam.identity.IdentityStatus;
import com.sca4d.iam.identity.PrincipalType;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/** 定义 Platform Account 目录、详情、状态修改和生命周期隔离使用的 SQL 形状。 */
public interface PlatformAccountMapper {

    /**
     * 查询一页 Platform Account。
     *
     * @param principalType PlatformPrincipal 持久化类型
     * @param search 可选用户名搜索词
     * @param status 可选账号状态
     * @param roleId 可选角色范围
     * @param assigned 提供角色时筛选已分配或未分配账号
     * @param offset 查询偏移量
     * @param size 最大返回数量
     * @return 当前页账号目录项
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull PlatformAccountEntry> selectPlatformAccounts(
            @Param("principalType") @NotNull PrincipalType principalType,
            @Param("search") @Nullable String search,
            @Param("status") @Nullable IdentityStatus status,
            @Param("roleId") @Nullable Long roleId,
            @Param("assigned") boolean assigned,
            @Param("offset") long offset,
            @Param("size") int size);

    /**
     * 统计 Platform Account 查询命中数。
     *
     * @param principalType PlatformPrincipal 持久化类型
     * @param search 可选用户名搜索词
     * @param status 可选账号状态
     * @param roleId 可选角色范围
     * @param assigned 提供角色时筛选已分配或未分配账号
     * @return 完整命中数量
     */
    @CheckReturnValue
    long countPlatformAccounts(
            @Param("principalType") @NotNull PrincipalType principalType,
            @Param("search") @Nullable String search,
            @Param("status") @Nullable IdentityStatus status,
            @Param("roleId") @Nullable Long roleId,
            @Param("assigned") boolean assigned);

    /**
     * 查询一个 Platform Account。
     *
     * @param accountId Account 标识
     * @param principalType PlatformPrincipal 持久化类型
     * @return 目录项；不存在时为空
     */
    @CheckReturnValue
    @Nullable
    PlatformAccountEntry selectPlatformAccount(
            @Param("accountId") @NotNull Long accountId,
            @Param("principalType") @NotNull PrincipalType principalType);

    /**
     * 批量查询未删除且关联 PlatformPrincipal 的 Platform Account。
     *
     * @param accountIds Account 标识
     * @param principalType PlatformPrincipal 持久化类型
     * @return 存在的 Account 目录项；缺失标识不生成空行
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull PlatformAccountEntry> selectPlatformAccountsByIds(
            @Param("accountIds") @NotNull Collection<@NotNull Long> accountIds,
            @Param("principalType") @NotNull PrincipalType principalType);

    /**
     * 修改 Platform Account 状态。
     *
     * @param accountId Account 标识
     * @param status 新状态
     * @return 修改行数
     */
    int updatePlatformAccountStatus(
            @Param("accountId") @NotNull Long accountId,
            @Param("status") @NotNull IdentityStatus status);

    /**
     * 停用并逻辑删除 Platform Account。
     *
     * @param accountId Account 标识
     * @return 修改行数
     */
    @CheckReturnValue
    int markPlatformAccountDeleted(@Param("accountId") @NotNull Long accountId);

    /**
     * 判断 Subject 是否还有目标以外的未删除 Account。
     *
     * @param subjectId Subject 标识
     * @param accountId 待删除 Account 标识
     * @return 存在时为 {@code true}
     */
    @CheckReturnValue
    boolean existsOtherAccountForSubject(
            @Param("subjectId") @NotNull Long subjectId,
            @Param("accountId") @NotNull Long accountId);

    /**
     * 判断 Principal 是否还有目标以外的未删除 Account。
     *
     * @param principalId PlatformPrincipal 标识
     * @param accountId 待删除 Account 标识
     * @return 存在时为 {@code true}
     */
    @CheckReturnValue
    boolean existsOtherAccountForPrincipal(
            @Param("principalId") @NotNull Long principalId,
            @Param("accountId") @NotNull Long accountId);

    /** 停用并逻辑删除 Account 的全部登录标识。 */
    @CheckReturnValue
    int markAccountIdentifiersDeleted(@Param("accountId") @NotNull Long accountId);

    /** 物理删除 Account 的全部 Principal 关系。 */
    @CheckReturnValue
    int deleteAccountPrincipalLinks(@Param("accountId") @NotNull Long accountId);

    /** 停用并逻辑删除不再被 Account 使用的 Principal。 */
    int markPrincipalDeleted(@Param("principalId") @NotNull Long principalId);

    /** 停用并逻辑删除不再拥有 Account 的 Subject。 */
    int markSubjectDeleted(@Param("subjectId") @NotNull Long subjectId);
}
