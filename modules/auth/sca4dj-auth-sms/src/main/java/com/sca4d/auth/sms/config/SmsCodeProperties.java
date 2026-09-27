package com.sca4d.auth.sms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置 PHONE 登录标识使用的 Account 认证域。 */
@ConfigurationProperties("sca4dj.auth.sms-code")
public class SmsCodeProperties {
    /** PHONE 登录标识所属的 Account 认证域，默认值为 `platform`。 */
    private String accountRealm = "platform";

    /** @return PHONE 登录标识所属的 Account 认证域 */
    public String getAccountRealm() {
        return accountRealm;
    }

    /** 更新 PHONE 登录标识所属的 Account 认证域。 */
    public void setAccountRealm(String accountRealm) {
        if (accountRealm == null || accountRealm.isBlank())
            throw new IllegalArgumentException("SMS Code Realm 不能为空");
        this.accountRealm = accountRealm;
    }
}
