package com.sca4d.authentication;

import com.sca4d.authentication.recovery.ConsumedAccountRecovery;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Session;
import com.sca4d.common.auth.Subject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 创建认证、提交具体认证方式，并创建 Session 或授权敏感操作。 */
public interface AuthenticationProcessService {

    /**
     * 为匿名请求创建 LOGIN、REGISTRATION 或 RECOVERY 认证。
     *
     * @param purpose 认证目的
     * @param scopeType GLOBAL 或 TENANT；为空时按 GLOBAL 处理
     * @param tenantCode TENANT 的业务编码
     * @param browserHash 当前浏览器 Cookie 摘要
     * @param deviceId 客户端设备标识
     * @param terminalName 客户端展示名称
     * @return 等待提交主要认证方式的认证
     */
    @NotNull
    AuthenticationProcess createAnonymous(
            @NotNull AuthenticationPurpose purpose,
            @Nullable AccountScopeType scopeType,
            @Nullable String tenantCode,
            @NotNull String browserHash,
            @Nullable String deviceId,
            @Nullable String terminalName);

    /**
     * 使用当前 Session 的 Subject 和 Account 创建 STEP_UP 或 LINKING 认证。
     *
     * @param purpose STEP_UP 或 LINKING
     * @param subject 当前 Session 的 Subject
     * @param account 当前 Session 固定的 Account
     * @param browserHash 当前浏览器 Cookie 摘要
     * @param deviceId 客户端设备标识
     * @param terminalName 客户端展示名称
     * @return 等待提交主要认证方式的认证
     */
    @NotNull
    AuthenticationProcess createAuthenticated(
            @NotNull AuthenticationPurpose purpose,
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull String browserHash,
            @Nullable String deviceId,
            @Nullable String terminalName);

    /**
     * 为当前主要认证任务选定认证方式。
     *
     * @param authenticationId 认证 UUID
     * @param browserHash 当前浏览器 Cookie 摘要
     * @param methodType Password、Passkey、OIDC、OAuth2 或 LDAP 等具体方式
     * @return 已记录所选认证方式的认证
     */
    @NotNull
    AuthenticationProcess claimPrimary(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull AuthMethodType methodType);

    /**
     * 确认当前任务允许准备或提交指定认证方式。
     *
     * @return 当前认证及其 Subject、Account 和任务编码
     */
    @NotNull
    AuthenticationProcess requireProof(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull AuthMethodType methodType);

    /**
     * 验证当前请求提交的密码、验证码、Token、LDAP 密码或 WebAuthn 响应，并完成当前任务。
     *
     * @param proofClass 当前认证方式明确接收的 Java 类型
     * @param proof 只在本次方法调用中使用的认证输入
     * @return 等待下一项输入或等待创建 Session 的认证
     */
    @NotNull
    <P> AuthenticationProcess proof(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull AuthMethodType methodType,
            @NotNull Class<P> proofClass,
            @NotNull P proof);

    /** @return 与当前浏览器绑定且尚未过期的认证 */
    @NotNull
    AuthenticationProcess requireCurrent(
            @NotNull String authenticationId, @NotNull String browserHash);

    /** 确认 STEP_UP 仍有效，并确认认证中的 Subject 和 Account 与当前 Session 相同。 */
    void authorizeStepUp(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull Subject subject,
            @NotNull Account account);

    /** @return 单次完成 LINKING 后的认证 */
    @NotNull
    AuthenticationProcess completeLinking(
            @NotNull String authenticationId, @NotNull String browserHash);

    /** @return 单次完成 RECOVERY 后重新读取的 Subject 和 Account */
    @NotNull
    ConsumedAccountRecovery consumeRecovery(
            @NotNull String authenticationId, @NotNull String browserHash);

    /** @return 使用已完成 LOGIN 或 REGISTRATION 认证创建的新 Session */
    @NotNull
    Session session(@NotNull String authenticationId, @NotNull String browserHash);
}
