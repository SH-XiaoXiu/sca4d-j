package com.sca4d.auth.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置 Email 登录标识使用的 Account 认证域。 */
@ConfigurationProperties("sca4dj.auth.email-code")
public final class EmailCodeProperties {

    private String accountRealm = "platform";

    /** 创建使用 platform 认证域的默认配置。 */
    public EmailCodeProperties() {
    }

    /** @return Email 登录标识使用的认证域 */
    public String getAccountRealm() {
        return accountRealm;
    }

    /** @param accountRealm 非空白 Account 认证域 */
    public void setAccountRealm(String accountRealm) {
        if (accountRealm == null || accountRealm.isBlank()) {
            throw new IllegalArgumentException("Email Code Account Realm 不能为空");
        }
        this.accountRealm = accountRealm;
    }
}
