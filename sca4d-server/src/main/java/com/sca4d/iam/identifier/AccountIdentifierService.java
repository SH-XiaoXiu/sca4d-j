package com.sca4d.iam.identifier;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.entity.IamAccountIdentifierEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** 创建、查询并按身份作用域解析 AccountIdentifier。 */
public interface AccountIdentifierService {

    /**
     * 为 Account 创建一个 USERNAME Identifier。
     *
     * @param accountId 所属 Account 主键
     * @param username 用户输入的本地登录名
     * @return 已写入数据库的 Identifier
     */
    @NotNull
    IamAccountIdentifierEntity createUsername(@NotNull Long accountId, @NotNull String username);

    /**
     * 按身份作用域解析一个可用登录标识。
     *
     * @param scopeType GLOBAL 或 TENANT
     * @param scopeId TENANT 作用域主键；GLOBAL 为 {@code null}
     * @param type USERNAME 或 EMAIL
     * @param value 用户提交的登录标识
     * @return 唯一 ACTIVE Identifier；不存在时为 {@code null}
     */
    @Nullable
    IamAccountIdentifierEntity findActive(
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId,
            @NotNull AccountIdentifierType type,
            @NotNull String value);

    /**
     * 读取 Account 当前全部未删除 Identifier。
     *
     * @param accountId Account 主键
     * @return 按类型和主键排序的 Identifier
     */
    @NotNull
    List<IamAccountIdentifierEntity> findByAccount(@NotNull Long accountId);

    /**
     * 批量读取多个 Account 当前未删除的登录标识。
     *
     * @param accountIds Account 主键列表
     * @return 每个输入 Account 对应的 Identifier 集合
     */
    @NotNull
    Map<Long, List<IamAccountIdentifierEntity>> findByAccounts(@NotNull List<Long> accountIds);
}
