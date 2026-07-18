package com.sca4d.iam.account;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.IamAccountEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** 创建和读取 Subject 名下的登录 Account。 */
public interface AccountService {

    /**
     * 创建一个可登录 Account。
     *
     * @param subjectId Account 所属 Subject
     * @param scopeType GLOBAL 或 TENANT
     * @param scopeId TENANT Account 所属租户；GLOBAL Account 为 null
     * @return 已写入数据库的 Account
     * @throws com.sca4d.common.exception.BizException Subject 不存在或同一作用域 Account 已存在时抛出
     */
    @NotNull
    IamAccountEntity create(
            @NotNull Long subjectId,
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId);

    /**
     * 读取一个未删除 Account。
     *
     * @param accountId Account 主键
     * @return 当前 Account
     * @throws com.sca4d.common.exception.BizException Account 不存在时抛出
     */
    @NotNull
    IamAccountEntity require(@NotNull Long accountId);

    /**
     * 读取一个 Subject 名下全部未删除 Account。
     *
     * @param subjectId Subject 主键
     * @return 按 Account 主键排序的集合
     */
    @NotNull
    List<IamAccountEntity> findBySubject(@NotNull Long subjectId);

    /**
     * 批量读取多个 Subject 名下的全部未删除 Account。
     *
     * @param subjectIds Subject 主键列表
     * @return 每个输入 Subject 对应的 Account 集合
     */
    @NotNull
    Map<Long, List<IamAccountEntity>> findBySubjects(@NotNull List<Long> subjectIds);

    /** 分页读取指定作用域内的 Account。 */
    @NotNull
    PageData<ManagedAccount> page(
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId,
            @Nullable String search,
            @Nullable AccountStatus status,
            long page,
            long size);

    /**
     * 修改创建 Session 前是否必须完成独立第二认证因素。
     *
     * @param accountId Account 主键
     * @param required 是否要求第二因子
     * @param version 当前 Account 版本
     * @return 修改后的 Account
     */
    @NotNull
    IamAccountEntity changeSecondFactorRequirement(
            @NotNull Long accountId, boolean required, long version);
}
