package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.config.properties.Sca4djCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** 验证缓存名称、过期时间和 Redis 序列化配置。 */
class CacheConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withUserConfiguration(CacheConfig.class);

    @Test
    void shouldRegisterRedisAndLocalCacheManagers() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Sca4djCacheProperties.class);
            assertThat(context).hasBean(CacheConfig.CACHE_MANAGER);
            assertThat(context).hasBean(CacheConfig.REDIS_CACHE_MANAGER);
            assertThat(context).hasBean(CacheConfig.LOCAL_CACHE_MANAGER);
            assertThat(context.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class);
            assertThat(context.getBean(CacheConfig.LOCAL_CACHE_MANAGER)).isInstanceOf(CaffeineCacheManager.class);
        });
    }
}
