package com.sca4d.authentication;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/** 验证一种认证输入，声明允许的认证目的，并在创建会话前复核验证结果。 */
public interface AuthMethod<P> {

    /** @return 对外稳定的认证方式类型 */
    @NotNull
    AuthMethodType type();

    /** @return 当前认证方式接收的 Java 类型 */
    @NotNull
    Class<P> proofClass();

    /** @return 当前认证方式允许用于哪些认证目的 */
    @NotNull
    Set<AuthMethodCapability> capabilities();

    /**
     * 在当前认证的账号范围内验证密码、验证码、Token、LDAP 密码或 WebAuthn 响应。
     *
     * @param authentication 当前认证及其已经确定的 Account
     * @param proof 当前请求提交且仅在本次方法调用中使用的认证输入
     * @return Subject、Account、凭据或外部身份 ID 及其版本号
     */
    @NotNull
    AuthMethodVerification verify(@NotNull AuthenticationProcess authentication, @NotNull P proof);

    /**
     * 在创建 Session 前重新确认先前使用的凭据或外部身份仍然有效。
     *
     * @param verification Flowable 保存的认证方式、Subject、Account、凭据或外部身份 ID 及版本号
     * @return 使用当前数据库记录重新确认的验证结果
     */
    @NotNull
    AuthMethodVerification requireCurrent(@NotNull AuthMethodVerification verification);
}
