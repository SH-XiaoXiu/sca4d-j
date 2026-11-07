package com.sca4d.auth.sms.config;

import com.sca4d.core.api.identity.RealmKey;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置 PHONE 登录标识使用的 Account 认证域。 */
@ConfigurationProperties("sca4dj.auth.sms-code")
public class SmsCodeProperties {
    /** PHONE 登录标识所属的 Account 认证域，默认值为 `PLATFORM`。 */
    private String accountRealm = "PLATFORM";

    /** @return PHONE 登录标识所属的 Account 认证域 */
    public String getAccountRealm() {
        return accountRealm;
    }

    /** 更新 PHONE 登录标识所属的 Account 认证域。 */
    public void setAccountRealm(String accountRealm) {
        this.accountRealm = new RealmKey(accountRealm).value();
    }
}
