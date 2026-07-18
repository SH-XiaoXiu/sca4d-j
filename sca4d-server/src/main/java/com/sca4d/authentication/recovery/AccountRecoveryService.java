package com.sca4d.authentication.recovery;

import com.sca4d.common.auth.PasswordCredential;
import org.jetbrains.annotations.NotNull;

/** 使用已验证的 RECOVERY Authentication 替换密码并结束该 Account 的全部登录会话。 */
public interface AccountRecoveryService {

    /**
     * 使用已验证的 RECOVERY Authentication 替换现有 PASSWORD Credential。
     *
     * <p>Authentication 和恢复码均不会在密码写入失败后恢复；客户端必须重新认证并使用另一条恢复码。
     *
     * @param authenticationId 已完成恢复验证的 Authentication
     * @param browserHash 当前浏览器 Cookie 摘要
     * @param password 只在当前调用中交给 PASSWORD CredentialScheme 的新密码
     */
    void replacePassword(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull PasswordCredential password);
}
