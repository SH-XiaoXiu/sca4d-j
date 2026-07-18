package com.sca4d.authentication.audit;

import com.sca4d.common.audit.AuditOperationContributor;
import com.sca4d.common.audit.AuditOperationDefinition;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

/** 登录、账号安全设置和登录会话操作使用的审计动作码。 */
@Component
public class AuthenticationAuditOperations implements AuditOperationContributor {

    /** 密码登录记录使用的审计模块编码。 */
    public static final String LOGIN = "authentication.login";
    /** 登录会话结束记录使用的审计模块编码。 */
    public static final String SESSIONS = "authentication.sessions";
    /** Account 认证安全设置记录使用的审计模块编码。 */
    public static final String ACCOUNT_SECURITY = "authentication.account-security";
    /** Account 恢复证明和密码替换记录使用的审计模块编码。 */
    public static final String RECOVERY = "authentication.recovery";
    /** 使用密码认证并创建 Session 的动作编码。 */
    public static final String PASSWORD = "password";
    /** 投递验证码 Challenge 的动作编码。 */
    public static final String EMAIL_CODE_CHALLENGE = "email-code-challenge";
    /** 验证一次性验证码 Proof 的动作编码。 */
    public static final String EMAIL_CODE = "email-code";
    /** 使用已完成认证创建 Session 的动作编码。 */
    public static final String SESSION = "session";
    /** 结束当前 Session 的动作编码。 */
    public static final String LOGOUT = "logout";
    /** 撤销当前 Subject 名下一个登录 Session 的动作编码。 */
    public static final String REVOKE = "revoke";
    /** 修改 Account 第二因子要求并替换或撤销恢复码的动作编码。 */
    public static final String SECOND_FACTOR_REQUIREMENT = "second-factor-requirement";
    /** 单次消费恢复码并创建 RECOVERY Proof 的动作编码。 */
    public static final String RECOVERY_CODE = "recovery-code";
    /** 使用已验证 RECOVERY 认证 替换账号密码的动作编码。 */
    public static final String PASSWORD_REPLACEMENT = "password-replacement";
    /** 验证 TOTP Proof 的动作编码。 */
    public static final String TOTP = "totp";
    /** 创建 TOTP enrollment 的动作编码。 */
    public static final String TOTP_ENROLLMENT = "totp-enrollment";
    /** 使用首次验证码激活 TOTP Credential 的动作编码。 */
    public static final String TOTP_ACTIVATION = "totp-activation";
    /** 使用新 enrollment 替换 TOTP Credential 的动作编码。 */
    public static final String TOTP_REPLACEMENT = "totp-replacement";
    /** 删除 TOTP Credential 的动作编码。 */
    public static final String TOTP_REMOVAL = "totp-removal";
    /** 创建 Passkey assertion challenge 的动作编码。 */
    public static final String PASSKEY_ASSERTION = "passkey-assertion";
    /** 验证 Passkey assertion Proof 的动作编码。 */
    public static final String PASSKEY = "passkey";
    /** 使用外部 OpenID Provider 完成认证的动作编码。 */
    public static final String OIDC = "oidc";
    /** 使用标准 OAuth2 Provider 完成认证的动作编码。 */
    public static final String OAUTH2 = "oauth2";
    /** 使用企业目录账号和密码完成 LDAP bind 的动作编码。 */
    public static final String LDAP = "ldap";
    /** 创建 Passkey registration challenge 的动作编码。 */
    public static final String PASSKEY_REGISTRATION = "passkey-registration";
    /** 使用 attestation 激活 Passkey Credential 的动作编码。 */
    public static final String PASSKEY_ACTIVATION = "passkey-activation";
    /** 删除 Passkey Credential 的动作编码。 */
    public static final String PASSKEY_REMOVAL = "passkey-removal";
    /** 解除 Account 的 ExternalIdentity 关联。 */
    public static final String EXTERNAL_IDENTITY_REMOVAL = "external-identity-removal";

    /** @return 当前认证对象能够真实产生的登录与会话动作 */
    @Override
    public @NotNull List<AuditOperationDefinition> auditOperations() {
        return List.of(
                AuditOperationDefinition.standard(LOGIN, 5, PASSWORD, 10),
                AuditOperationDefinition.standard(LOGIN, 5, EMAIL_CODE_CHALLENGE, 20),
                AuditOperationDefinition.standard(LOGIN, 5, EMAIL_CODE, 30),
                AuditOperationDefinition.standard(LOGIN, 5, TOTP, 40),
                AuditOperationDefinition.standard(LOGIN, 5, PASSKEY_ASSERTION, 50),
                AuditOperationDefinition.standard(LOGIN, 5, PASSKEY, 60),
                AuditOperationDefinition.standard(LOGIN, 5, OIDC, 70),
                AuditOperationDefinition.standard(LOGIN, 5, OAUTH2, 80),
                AuditOperationDefinition.standard(LOGIN, 5, LDAP, 90),
                AuditOperationDefinition.standard(LOGIN, 5, SESSION, 100),
                AuditOperationDefinition.standard(SESSIONS, 6, LOGOUT, 10),
                AuditOperationDefinition.standard(SESSIONS, 6, REVOKE, 20),
                AuditOperationDefinition.standard(ACCOUNT_SECURITY, 7, SECOND_FACTOR_REQUIREMENT, 10),
                AuditOperationDefinition.standard(ACCOUNT_SECURITY, 7, TOTP_ENROLLMENT, 20),
                AuditOperationDefinition.standard(ACCOUNT_SECURITY, 7, TOTP_ACTIVATION, 30),
                AuditOperationDefinition.standard(ACCOUNT_SECURITY, 7, TOTP_REPLACEMENT, 40),
                AuditOperationDefinition.standard(ACCOUNT_SECURITY, 7, TOTP_REMOVAL, 50),
                AuditOperationDefinition.standard(ACCOUNT_SECURITY, 7, PASSKEY_REGISTRATION, 60),
                AuditOperationDefinition.standard(ACCOUNT_SECURITY, 7, PASSKEY_ACTIVATION, 70),
                AuditOperationDefinition.standard(ACCOUNT_SECURITY, 7, PASSKEY_REMOVAL, 80),
                AuditOperationDefinition.standard(ACCOUNT_SECURITY, 7, EXTERNAL_IDENTITY_REMOVAL, 90),
                AuditOperationDefinition.standard(RECOVERY, 8, RECOVERY_CODE, 10),
                AuditOperationDefinition.standard(RECOVERY, 8, PASSWORD_REPLACEMENT, 20));
    }
}
