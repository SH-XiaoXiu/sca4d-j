package com.sca4d.iam.account;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.PasswordCredential;
import com.sca4d.common.auth.SessionSnapshot;
import com.sca4d.common.auth.Subject;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 查询当前 Subject 的 Account 安全资料，并使用 STEP_UP 修改二次验证或撤销登录 Session。 */
public interface AccountSecurityService {

    /**
     * 查询 Subject 名下全部 Account、Identifier 和 Credential 安全表示。
     *
     * @param subjectId 当前登录 Subject 主键
     * @return 按 Account 主键排序的账号安全集合
     */
    @NotNull
    List<AccountSecurity> accounts(@NotNull Long subjectId);

    /**
     * 读取一个已完成授权的 Account 的非敏感认证安全资料。
     *
     * @param accountId 目标 Account 主键
     * @return 目标 Account 的认证安全资料
     */
    @NotNull
    AccountSecurity security(@NotNull Long accountId);

    /**
     * 查询 Subject 名下一个 Account 的活跃登录 Session。
     *
     * @param subjectId 当前登录 Subject 主键
     * @param accountId 目标 Account 主键
     * @return 按登录时间倒序排列且不包含 Token 的 Session
     */
    @NotNull
    List<SessionSnapshot> sessions(@NotNull Long subjectId, @NotNull Long accountId);

    /**
     * 读取一个已完成授权的 Account 的活跃登录 Session。
     *
     * @param accountId 目标 Account 主键
     * @return 不包含 Token 的登录 Session
     */
    @NotNull
    List<SessionSnapshot> managedSessions(@NotNull Long accountId);

    /**
     * 使用当前 Session Account 完成的 STEP_UP 修改目标 Account 第二因子要求。
     *
     * <p>启用或在已启用状态再次提交时替换恢复码；关闭时撤销恢复码。两种结果都会结束目标 Account 的全部旧登录会话。
     *
     * @param subject 当前 Session 固定的 Subject
     * @param authenticatedAccount 当前 Session 固定的 Account
     * @param targetAccountId 待修改的 Account
     * @param required 是否要求登录第二因子
     * @param version 目标 Account 当前版本
     * @param authenticationId 已验证 STEP_UP 认证 UUID
     * @param browserHash 当前浏览器 Cookie 摘要
     * @return 修改后的 Account；启用时包含仅本次展示的恢复码
     */
    @NotNull
    AccountSecondFactorChange changeSecondFactorRequirement(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            boolean required,
            long version,
            @NotNull String authenticationId,
            @NotNull String browserHash);

    /**
     * 使用操作账号完成的 STEP_UP 修改另一个已授权 Account 的 MFA 要求。
     *
     * <p>调用方必须先完成独立权限和 {@link AccountAccessPolicy} 校验。启用时签发首组恢复码，关闭时撤销恢复码；
     * 两种变更均终止目标 Account 的全部 Session。
     */
    @NotNull
    AccountSecondFactorChange changeManagedSecondFactorRequirement(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            boolean required,
            long version,
            @NotNull String authenticationId,
            @NotNull String browserHash);

    /**
     * 使用操作账号的 STEP_UP 为已启用 MFA 的 Account 重签恢复码。
     *
     * <p>调用方必须先完成恢复码独立权限和 {@link AccountAccessPolicy} 校验；重签会推进 Account 版本并终止目标
     * Account 的全部 Session。
     */
    @NotNull
    AccountSecondFactorChange reissueManagedRecoveryCodes(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            long version,
            @NotNull String authenticationId,
            @NotNull String browserHash);

    /**
     * 使用操作账号的 STEP_UP 为已授权 Account 绑定 PASSWORD Credential。
     *
     * <p>调用方必须先完成独立权限和 {@link AccountAccessPolicy} 校验；写入后只终止目标 Account 的
     * Session。
     */
    @NotNull
    Long bindManagedPassword(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull String identifier,
            @NotNull PasswordCredential credential,
            @NotNull String authenticationId,
            @NotNull String browserHash);

    /**
     * 使用操作账号的 STEP_UP 和 Credential If-Match 重置已授权 Account 的 PASSWORD Credential。
     */
    void replaceManagedPassword(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull Long credentialId,
            long credentialVersion,
            @NotNull PasswordCredential credential,
            @NotNull String authenticationId,
            @NotNull String browserHash);

    /**
     * 使用当前 Session Account 完成的 STEP_UP 撤销一个登录 Session。
     *
     * @param subject 当前 Session 固定的 Subject
     * @param authenticatedAccount 当前 Session 固定的 Account
     * @param targetAccountId 待撤销 Session 所属 Account
     * @param sessionId 不能用于认证的项目 Session 标识
     * @param authenticationId 已验证 STEP_UP 认证 UUID
     * @param browserHash 当前浏览器 Cookie 摘要
     */
    void endSession(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull String sessionId,
            @NotNull String authenticationId,
            @NotNull String browserHash);

    /**
     * 使用操作账号的 STEP_UP 撤销已授权 Account 的一个 Session。
     *
     * <p>调用方必须先完成独立权限和 {@link AccountAccessPolicy} 校验。
     */
    void endManagedSession(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull String sessionId,
            @NotNull String authenticationId,
            @NotNull String browserHash);

    /** 使用操作账号的 STEP_UP 撤销已授权 Account 的全部 Session。 */
    void endManagedSessions(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull String authenticationId,
            @NotNull String browserHash);
}
