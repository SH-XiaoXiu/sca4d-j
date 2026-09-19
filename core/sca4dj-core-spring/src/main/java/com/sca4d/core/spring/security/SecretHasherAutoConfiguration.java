package com.sca4d.core.spring.security;

import com.sca4d.core.api.security.SecretHasher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** 为需要保存不可逆秘密值摘要的模块提供默认 Argon2id 实现。 */
@AutoConfiguration
public class SecretHasherAutoConfiguration {

    /** 创建秘密值摘要自动配置。 */
    public SecretHasherAutoConfiguration() {
    }

    /** @return 应用没有替换时使用的 Argon2id 秘密值摘要实现 */
    @Bean
    @ConditionalOnMissingBean
    SecretHasher secretHasher() {
        return new Argon2SecretHasher();
    }
}
