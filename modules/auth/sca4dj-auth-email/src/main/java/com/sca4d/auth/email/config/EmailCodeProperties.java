package com.sca4d.auth.email.config;

import com.sca4d.core.api.identity.RealmKey;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置 Email 登录标识使用的 Account 认证域。 */
@ConfigurationProperties("sca4dj.auth.email-code")
public class EmailCodeProperties {

    private String accountRealm = "PLATFORM";

    public EmailCodeProperties() {
    }

    /** @return Email 登录标识使用的认证域 */
    public String getAccountRealm() {
        return accountRealm;
    }

    /** @param accountRealm 符合 {@link RealmKey} 格式的 Account 认证域 */
    public void setAccountRealm(String accountRealm) {
        this.accountRealm = new RealmKey(accountRealm).value();
    }
}
