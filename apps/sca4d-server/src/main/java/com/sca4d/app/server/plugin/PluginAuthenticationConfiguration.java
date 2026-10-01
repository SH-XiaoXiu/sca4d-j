package com.sca4d.app.server.plugin;

import com.sca4d.auth.config.AuthSettings;
import com.sca4d.auth.engine.AuthenticationMethods;
import com.sca4d.auth.engine.RedisAuthenticationAttempts;
import com.sca4d.auth.engine.attempt.RedisAuthenticationAttemptStore;
import com.sca4d.core.api.authentication.AuthenticationAttempts;
import com.sca4d.core.api.authentication.AuthenticationPolicy;
import com.sca4d.core.api.plugin.PluginScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 为 App 装配普通认证入口，并声明一次操作固定 MFA 插件代次。 */
@Configuration(proxyBeanMethods = false)
public class PluginAuthenticationConfiguration {
    /** 复用认证引擎实现，插件作用域由宿主 Spring 适配在 Bean 外建立。 */
    @Bean
    @PluginScope
    AuthenticationAttempts authenticationAttempts(
            AuthenticationMethods methods,
            RedisAuthenticationAttemptStore attempts,
            AuthenticationPolicy policy,
            AuthSettings settings,
            Clock clock) {
        return new RedisAuthenticationAttempts(methods, attempts, policy, settings, clock);
    }
}
