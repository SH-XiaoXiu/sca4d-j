package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

/** 验证 Redis 密码环境变量写入 Spring 配置且不会覆盖显式配置。 */
class RedisPasswordEnvironmentPostProcessorTest {

    @Test
    void shouldIgnoreBlankRedisPassword() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of("SCA4DJ_REDIS_PASSWORD", "")));

        new RedisPasswordEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.data.redis.password")).isNull();
    }

    @Test
    void shouldBindTextRedisPassword() {
        MockEnvironment environment = new MockEnvironment();
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("SCA4DJ_REDIS_PASSWORD", "secret")));

        new RedisPasswordEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.data.redis.password")).isEqualTo("secret");
    }
}
