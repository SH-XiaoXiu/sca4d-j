package com.sca4d.auth.x509;

import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.identity.AccountDirectory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/** 自动装配 Account X.509 Credential、认证方法和生命周期服务。 */
@AutoConfiguration
@MapperScan(basePackageClasses = X509CredentialMapper.class)
public class X509AutoConfiguration {

    /** @return X.509 Credential Repository */
    @Bean
    X509CredentialRepository x509CredentialRepository(X509CredentialMapper mapper) {
        return new X509CredentialRepository(mapper);
    }

    /** @return 认证引擎可发现并负责 Credential 生命周期的 X.509 方法 */
    @Bean
    X509AuthenticationMethod x509AuthenticationMethod(
            X509CredentialRepository credentials,
            AccountDirectory accounts,
            SessionControl sessions,
            IdGenerator ids,
            Clock clock) {
        return new X509AuthenticationMethod(credentials, accounts, sessions, ids, clock);
    }
}
