package com.sca4d.app.server.authentication;

import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.LogicSetting;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 注册 Sca4d Server 按 Account 保存的认证策略设置。 */
@Configuration(proxyBeanMethods = false)
public class AccountAuthenticationSettingConfiguration {

    /** Account 登录和敏感动作使用的完整认证策略。 */
    public static final @NotNull LogicSetting<AccountAuthenticationSettings> ACCOUNT_POLICY = new LogicSetting<>(
            "authentication.account-policy",
            1,
            AccountAuthenticationSettings.defaults(),
            new AccountAuthenticationSettingsCodec(),
            Pipeline.identity(AccountAuthenticationSettings.class),
            false);

    /** 创建账号认证策略设置配置。 */
    public AccountAuthenticationSettingConfiguration() {
    }

    /** @return Account 认证策略设置定义 */
    @Bean
    LogicSetting<AccountAuthenticationSettings> accountAuthenticationPolicySetting() {
        return ACCOUNT_POLICY;
    }
}
