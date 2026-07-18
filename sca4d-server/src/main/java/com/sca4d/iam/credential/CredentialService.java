package com.sca4d.iam.credential;

import com.sca4d.entity.IamCredentialEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/** 按认证材料 Java 类型选择 CredentialScheme，并绑定、替换或校验认证材料。 */
public interface CredentialService {

    /**
     * 为 Account 绑定新的认证材料。
     *
     * @param accountId 认证材料所属的 Account 主键
     * @param credential 已转换且类型明确的认证材料
     */
    <C> @NotNull Long bind(@NotNull Long accountId, @NotNull C credential);

    /**
     * 替换 Account 当前已经绑定的认证材料。
     *
     * @param accountId 认证材料所属的 Account 主键
     * @param credential 已转换且类型明确的新认证材料
     */
    <C> void replace(
            @NotNull Long accountId,
            @NotNull Long credentialId,
            @NotNull Long version,
            @NotNull C credential);

    /**
     * 读取一个 Subject 当前可由已注册 Scheme 管理的全部凭证。
     *
     * @param subjectId 目标 Subject 主键
     * @return 不包含摘要、盐和秘密材料的当前凭证集合
     */
    @NotNull
    List<IamCredentialEntity> findBySubject(@NotNull Long subjectId);

    /**
     * 读取一个 Account 当前可由已注册 Scheme 管理的全部凭证。
     *
     * @param accountId 目标 Account 主键
     * @return 不包含摘要、盐和秘密材料的当前凭证集合
     */
    @NotNull
    List<IamCredentialEntity> findByAccount(@NotNull Long accountId);

    /**
     * 批量读取多个 Subject 当前可由已注册 Scheme 管理的凭证。
     *
     * @param subjectIds 需要补全登录账号的 Subject 主键集合
     * @return 以 Subject 主键索引的安全凭证集合；无凭证的 Subject 映射为空集合
     */
    @NotNull
    Map<Long, List<IamCredentialEntity>> findBySubjects(@NotNull List<Long> subjectIds);

    /**
     * 变更一个当前凭证的启停状态。
     *
     * @param accountId 凭证所属 Account 主键
     * @param credentialId Credential 主键
     * @param status 目标状态
     * @param version 当前 Credential 版本
     */
    void changeStatus(
            @NotNull Long accountId,
            @NotNull Long credentialId,
            @NotNull CredentialStatus status,
            @NotNull Long version);

    /**
     * 逻辑删除一个当前凭证。
     *
     * @param accountId 凭证所属 Account 主键
     * @param credentialId Credential 主键
     * @param version 当前 Credential 版本
     */
    void delete(@NotNull Long accountId, @NotNull Long credentialId, @NotNull Long version);

    /**
     * 使用类型明确的 Proof 验证指定 Account。
     *
     * @param accountId 已由 Identifier、ExternalIdentity 或 Flow 解析的 Account 主键
     * @param proof 当前认证操作提交的类型化 Proof
     * @return 完成验证的 Subject、Account、Credential 和版本
     */
    @NotNull
    <P> CredentialVerification verify(@NotNull Long accountId, @NotNull P proof);

    /**
     * 确认验证结果引用的 Credential、Account 及 Subject 仍保持验证时状态。
     *
     * @param verification 先前完成的认证材料验证结果
     * @return 使用当前数据库状态重新构造的验证结果
     * @throws com.sca4d.common.auth.UnauthenticatedException Credential 已变化或 Account、Subject 已不可认证时抛出
     */
    @NotNull
    CredentialVerification requireCurrent(@NotNull CredentialVerification verification);
}
