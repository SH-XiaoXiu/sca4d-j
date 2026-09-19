package com.sca4d.auth.hmac;

import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.spring.redis.Sca4djRedis;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.SecureRandom;
import java.time.Clock;

/** 自动装配 Account HMAC Credential、认证方法和生命周期服务。 */
@AutoConfiguration
@MapperScan(basePackageClasses = HmacCredentialMapper.class)
@EnableConfigurationProperties(HmacProperties.class)
public class HmacAutoConfiguration {

    /** @return HMAC Credential Repository */
    @Bean
    HmacCredentialRepository hmacCredentialRepository(HmacCredentialMapper mapper) {
        return new HmacCredentialRepository(mapper);
    }

    /** @return HMAC Key 加密器 */
    @Bean
    HmacKeyCryptography hmacKeyCryptography(SecretKeyring keyring) {
        return new HmacKeyCryptography(keyring);
    }

    /** @return HMAC nonce 和失败窗口存储 */
    @Bean
    HmacAuthenticationAttemptStore hmacAuthenticationAttemptStore(
            @Sca4djRedis StringRedisTemplate redis,
            RuntimeNamespace namespace) {
        return new HmacAuthenticationAttemptStore(redis, namespace);
    }

    /** @return 认证引擎可发现并负责 Credential 生命周期的 HMAC 方法 */
    @Bean
    HmacAuthenticationMethod hmacAuthenticationMethod(
            HmacCredentialRepository credentials,
            HmacKeyCryptography cryptography,
            SessionControl sessions,
            HmacAuthenticationAttemptStore attempts,
            Clock clock,
            HmacProperties properties) {
        return new HmacAuthenticationMethod(
                credentials,
                cryptography,
                sessions,
                new SecureRandom(),
                attempts,
                clock,
                properties);
    }
}
