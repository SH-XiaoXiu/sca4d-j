package com.sca4d.auth.password.config;

import com.sca4d.auth.password.PasswordAuthenticationMethod;
import com.sca4d.auth.password.credential.PasswordCredentialRepository;
import com.sca4d.auth.password.credential.mapper.PasswordCredentialMapper;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.security.SecretHasher;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.time.Clock;

/**
 * 按 Spring Boot 自动配置约定装配 Password 认证方法、凭据持久化和 bootstrap 任务。
 *
 * <p>本自动配置只注册密码认证 Java API，不创建或注册任何 HTTP 路由。
 */
@AutoConfiguration
@MapperScan(basePackageClasses = PasswordCredentialMapper.class)
@EnableConfigurationProperties(PasswordProperties.class)
public class PasswordAutoConfiguration {

    /** 创建 Password 模块自动配置。 */
    public PasswordAutoConfiguration() {
    }

    /**
     * @param mapper Password 凭据 Mapper
     * @return Password 模块的密码凭据 Repository
     */
    @Bean
    PasswordCredentialRepository passwordCredentialRepository(PasswordCredentialMapper mapper) {
        return new PasswordCredentialRepository(mapper);
    }

    /**
     * @param accounts 定位密码登录账号的目录
     * @param credentials 读写密码凭据的 Repository
     * @param hasher 生成和校验密码摘要的实现
     * @param sessions 终止账号旧 Session 的控制入口
     * @param properties 账号认证域和 bootstrap 配置
     * @param clock 记录认证时间的时钟
     * @return 可被认证引擎发现并负责 Password Credential 的方法
     */
    @Bean
    PasswordAuthenticationMethod passwordAuthenticationMethod(
            AccountDirectory accounts,
            PasswordCredentialRepository credentials,
            SecretHasher hasher,
            SessionControl sessions,
            Clock clock,
            PasswordProperties properties) {
        return new PasswordAuthenticationMethod(
                accounts,
                new RealmKey(properties.getAccountRealm()),
                properties.getLoginIdentifierTypes()
                        .stream()
                        .map(AccountIdentifierType::new)
                        .toList(),
                credentials,
                hasher,
                sessions,
                clock);
    }

    /**
     * @param passwords Password 认证方法
     * @param properties bootstrap 账号和原始密码配置
     * @return 在 IAM 初始化后补充初始密码凭据的启动任务
     */
    @Bean
    @Order(100)
    @ConditionalOnProperty(prefix = "sca4dj.auth.password", name = "bootstrap-enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner passwordBootstrap(PasswordAuthenticationMethod passwords, PasswordProperties properties) {
        // IAM 初始化任务先创建平台账号；本任务随后仅为该账号缺失的密码凭据写入初始摘要。
        return arguments -> passwords.bootstrap(properties.getBootstrapUsername(), properties.getBootstrapPassword());
    }
}
