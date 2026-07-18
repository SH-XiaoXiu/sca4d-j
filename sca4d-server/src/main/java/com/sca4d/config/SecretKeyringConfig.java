package com.sca4d.config;

import com.sca4d.common.security.SecretKeyring;
import com.sca4d.config.properties.Sca4djSecretKeyringProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 注册 SMTP 密码、Provider 密钥和 TOTP 密钥使用的加密配置。 */
@Configuration
@EnableConfigurationProperties(Sca4djSecretKeyringProperties.class)
public class SecretKeyringConfig {

    /**
     * 在应用启动时解析全部配置密钥；配置无效时拒绝启动。
     *
     * @param properties active key 和可读取旧密钥
     * @return SMTP、Provider 与 TOTP 共用的加密对象
     */
    @Bean
    public @NotNull SecretKeyring secretKeyring(
            @NotNull Sca4djSecretKeyringProperties properties) {
        return new SecretKeyring(properties);
    }
}
