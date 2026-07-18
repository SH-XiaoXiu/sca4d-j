package com.sca4d.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Redis 密码环境处理器。
 *
 * <p>{@code SCA4DJ_REDIS_PASSWORD=} 表示无密码，不能绑定成空字符串。Redisson starter
 * 遇到空字符串密码时仍会执行 AUTH，导致无密码 Redis 启动失败。
 */
public class RedisPasswordEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String ENV_PASSWORD = "SCA4DJ_REDIS_PASSWORD";
    private static final String SPRING_PASSWORD = "spring.data.redis.password";
    private static final String PROPERTY_SOURCE_NAME = "sca4dRedisPassword";

    @Override
    public void postProcessEnvironment(
            @NotNull ConfigurableEnvironment environment, @NotNull SpringApplication application) {
        String password = environment.getProperty(ENV_PASSWORD);
        if (!StringUtils.hasText(password)) {
            return;
        }
        // 只有显式配置了非空密码时才写入 Spring Redis 密码，空串保持为未配置密码。
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(SPRING_PASSWORD, password)));
    }

    @Override
    public int getOrder() {
        // 等 Spring Boot 完成 application.yml 和 .env 导入后再做映射。
        return Ordered.LOWEST_PRECEDENCE;
    }
}
