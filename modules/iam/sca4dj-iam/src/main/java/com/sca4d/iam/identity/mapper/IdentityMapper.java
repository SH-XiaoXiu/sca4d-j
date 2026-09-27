package com.sca4d.iam.identity.mapper;

import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.iam.identity.AccountIdentifier;
import com.sca4d.iam.identity.IdentityStatus;
import com.sca4d.iam.identity.PrincipalType;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/** 读写 IAM 的 `Subject`、`Account`、`Principal` 和登录标识，不装配具体 `Principal` 实现。 */
public interface IdentityMapper {
    /**
     * 插入 `Subject`。
     *
     * @param subjects 非空 Subject 集合；标识已经去重，主键冲突时不覆盖
     * @param status 身份状态
     * @return 新增行数
     */
    @CheckReturnValue
    int insertSubjects(
            @Param("subjects") @NotNull Collection<Subject> subjects,
            @Param("status") @NotNull IdentityStatus status);

    /** 按标识和可选状态读取未删除 Subject。 */
    @CheckReturnValue
    @Nullable
    Subject selectSubject(@Param("id") @NotNull Long id, @Param("status") @Nullable IdentityStatus status);

    /** @return `Subject` 当前状态；不存在时为 {@code null} */
    @CheckReturnValue
    @Nullable
    IdentityStatus selectSubjectStatus(@Param("id") @NotNull Long id);

    /**
     * 按 ID 修改 `Subject` 状态。
     *
     * @param id `Subject` 标识
     * @param status 新状态
     * @return 修改行数
     */
    @CheckReturnValue
    int updateSubjectStatus(
            @Param("id") @NotNull Long id,
            @Param("status") @NotNull IdentityStatus status);

    /**
     * 插入 `Account`。
     *
     * @param id 账号标识
     * @param subjectId 所属 `Subject` 标识
     * @param realm 认证域
     * @param status 身份状态
     * @return 新增行数
     */
    @CheckReturnValue
    int insertAccount(
            @Param("id") @NotNull Long id,
            @Param("subjectId") @NotNull Long subjectId,
            @Param("realm") @NotNull RealmKey realm,
            @Param("status") @NotNull IdentityStatus status);

    /** 按账号、Subject 或关联 Principal 读取账号，可独立约束账号与 Subject 状态；必须提供一个标识范围。 */
    @CheckReturnValue
    @NotNull
    List<Account> selectAccounts(
            @Param("id") @Nullable Long id,
            @Param("subjectId") @Nullable Long subjectId,
            @Param("principalId") @Nullable Long principalId,
            @Param("status") @Nullable IdentityStatus status,
            @Param("subjectStatus") @Nullable IdentityStatus subjectStatus);

    /** @return 账号当前状态；不存在时为 {@code null} */
    @CheckReturnValue
    @Nullable
    IdentityStatus selectAccountStatus(@Param("id") @NotNull Long id);

    /**
     * 按标识修改账号状态。
     *
     * @param id 账号标识
     * @param status 新状态
     * @return 修改行数
     */
    @CheckReturnValue
    int updateAccountStatus(
            @Param("id") @NotNull Long id,
            @Param("status") @NotNull IdentityStatus status);

    /**
     * 插入 `Principal`。
     *
     * @param id 业务主体标识
     * @param principalType 稳定类型判别值
     * @param status 身份状态
     * @return 新增行数
     */
    @CheckReturnValue
    int insertPrincipal(
            @Param("id") @NotNull Long id,
            @Param("principalType") @NotNull PrincipalType principalType,
            @Param("status") @NotNull IdentityStatus status);

    /** @return 指定集合中尚未删除且符合可选类型、状态条件的业务主体标识 */
    @CheckReturnValue
    @NotNull
    List<Long> selectPrincipalIds(
            @Param("ids") @NotNull Collection<Long> ids,
            @Param("principalType") @Nullable PrincipalType principalType,
            @Param("status") @Nullable IdentityStatus status,
            @Param("linkedAccountRealm") @Nullable RealmKey linkedAccountRealm);

    /** @return 业务主体当前状态；不存在或类型不匹配时为 {@code null} */
    @CheckReturnValue
    @Nullable
    IdentityStatus selectPrincipalStatus(
            @Param("id") @NotNull Long id,
            @Param("principalType") @NotNull PrincipalType principalType);

    /** 按类型和当前删除标志批量更新主体状态；集合非空，非空字段不得 CLEAR。 */
    @CheckReturnValue
    int updatePrincipals(
            @Param("ids") @NotNull Collection<Long> ids,
            @Param("principalType") @Nullable PrincipalType principalType,
            @Param("expectedDeleted") boolean expectedDeleted,
            @Param("status") @NotNull Patch<IdentityStatus> status,
            @Param("deleted") @NotNull Patch<Boolean> deleted);

    /**
     * 幂等新增账号与业务主体关系。
     *
     * @param accountId 账号标识
     * @param principalId 业务主体标识
     * @return 新增行数
     */
    @CheckReturnValue
    int insertLink(
            @Param("accountId") @NotNull Long accountId,
            @Param("principalId") @NotNull Long principalId);

    /**
     * 删除账号与业务主体关系。
     *
     * @param accountId 账号标识
     * @param principalId 业务主体标识
     * @return 删除行数
     */
    @CheckReturnValue
    int deleteLink(
            @Param("accountId") @NotNull Long accountId,
            @Param("principalId") @NotNull Long principalId);

    /** 读取账号关联的未删除主体标识，并按可选类型、状态过滤。 */
    @CheckReturnValue
    @NotNull
    List<Long> selectLinkedPrincipalIds(
            @Param("accountId") @NotNull Long accountId,
            @Param("principalType") @Nullable PrincipalType principalType,
            @Param("status") @Nullable IdentityStatus status);

    /**
     * 批量查询业务主体与账号关系。
     *
     * @param principalIds 业务主体标识
     * @return 关系查询结果
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull PrincipalAccountLink> selectPrincipalAccountLinks(
            @Param("principalIds") @NotNull Collection<@NotNull Long> principalIds);

    /**
     * 新增登录标识。
     *
     * @param id 登录标识主键
     * @param accountId 账号标识
     * @param realm 认证域
     * @param type 登录标识类型
     * @param value 原始展示值
     * @param normalizedValue 稳定查找值
     * @param status 身份状态
     * @return 新增行数
     */
    @CheckReturnValue
    int insertIdentifier(
            @Param("id") long id,
            @Param("accountId") @NotNull Long accountId,
            @Param("realm") @NotNull RealmKey realm,
            @Param("type") @NotNull AccountIdentifierType type,
            @Param("value") @NotNull String value,
            @Param("normalizedValue") @NotNull String normalizedValue,
            @Param("status") @NotNull IdentityStatus status);

    /** 按登录标识主键或账号读取未删除标识；必须提供其中一个范围。 */
    @CheckReturnValue
    @NotNull
    List<AccountIdentifier> selectIdentifiers(
            @Param("id") @Nullable Long id,
            @Param("accountId") @Nullable Long accountId);

    /** 更新登录标识的展示值、规范值及生命周期字段，已删除行不可改写。 */
    @CheckReturnValue
    int updateIdentifier(
            @Param("id") long id,
            @Param("value") @NotNull Patch<String> value,
            @Param("normalizedValue") @NotNull Patch<String> normalizedValue,
            @Param("status") @NotNull Patch<IdentityStatus> status,
            @Param("deleted") @NotNull Patch<Boolean> deleted);

    /**
     * 用规范化标识解析完整可用身份链的账号。
     *
     * @param realm 认证域
     * @param type 登录标识类型
     * @param normalizedValue 稳定查找值
     * @return 账号查询结果；没有匹配时为 {@code null}
     */
    @CheckReturnValue
    @Nullable
    Long resolveActiveAccountId(
            @Param("realm") @NotNull RealmKey realm,
            @Param("type") @NotNull AccountIdentifierType type,
            @Param("normalizedValue") @NotNull String normalizedValue);
}
