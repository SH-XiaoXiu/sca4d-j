package com.sca4d.auth.sharedsecret;

import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.security.SecretHasher;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.security.SecureRandom;
import java.time.Clock;

/** 自动装配 Account Shared Secret 凭据、认证方法和生命周期服务。 */
@AutoConfiguration
@MapperScan(basePackageClasses = SharedSecretCredentialMapper.class)
public class SharedSecretAutoConfiguration {

    /** @return Shared Secret Repository */
    @Bean
    SharedSecretCredentialRepository sharedSecretCredentialRepository(SharedSecretCredentialMapper mapper) {
        return new SharedSecretCredentialRepository(mapper);
    }

    /** @return 认证引擎可发现并负责 Credential 生命周期的 Shared Secret 方法 */
    @Bean
    SharedSecretAuthenticationMethod sharedSecretAuthenticationMethod(
            SharedSecretCredentialRepository credentials,
            SecretHasher hasher,
            SessionControl sessions,
            Clock clock) {
        return new SharedSecretAuthenticationMethod(credentials, hasher, sessions, new SecureRandom(), clock);
    }
}
