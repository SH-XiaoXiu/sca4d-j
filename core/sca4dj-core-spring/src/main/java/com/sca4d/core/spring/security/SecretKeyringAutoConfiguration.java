package com.sca4d.core.spring.security;

import com.sca4d.core.api.security.SecretKeyring;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 当配置键 {@code sca4dj.secret-keyring.active-key-id} 存在且应用没有提供 SecretKeyring Bean 时，创建 AES-256-GCM 密钥环。
 */
@AutoConfiguration
@EnableConfigurationProperties(SecretKeyringProperties.class)
public class SecretKeyringAutoConfiguration {

    /** 创建 Secret keyring 自动配置。 */
    public SecretKeyringAutoConfiguration() {
    }

    /** @return 配置驱动的 Secret keyring */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sca4dj.secret-keyring", name = "active-key-id")
    SecretKeyring secretKeyring(SecretKeyringProperties properties) {
        return new ConfiguredSecretKeyring(properties);
    }
}
