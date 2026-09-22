package com.sca4d.auth.sms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置 PHONE 登录标识使用的 Account 认证域。 */
@ConfigurationProperties("sca4dj.auth.sms-code")
public final class SmsCodeProperties {
    private String accountRealm = "platform";

    public String getAccountRealm() {
        return accountRealm;
    }

    /** 执行 setAccountRealm 方法表示的业务操作。 */
    public void setAccountRealm(String accountRealm) {
        if (accountRealm == null || accountRealm.isBlank())
            throw new IllegalArgumentException("SMS Code Realm 不能为空");
        this.accountRealm = accountRealm;
    }
}
