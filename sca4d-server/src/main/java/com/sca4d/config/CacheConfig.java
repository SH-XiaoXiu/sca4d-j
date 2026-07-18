package com.sca4d.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sca4d.config.properties.Sca4djCacheProperties;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 缓存配置。
 *
 * <p>该配置只提供 Spring Cache 公共组件。业务是否缓存由业务模块自己决定：极简单点查可以使用
 * {@code @Cacheable}，复杂缓存必须放在业务自己的 cache 包下显式建模，业务代码不得直接依赖 RedisTemplate。
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(Sca4djCacheProperties.class)
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** Spring 默认缓存管理器名称。 */
    public static final String CACHE_MANAGER = "cacheManager";

    /** Redis 集群缓存管理器名称。 */
    public static final String REDIS_CACHE_MANAGER = "redisCacheManager";

    /** 本地缓存管理器名称。 */
    public static final String LOCAL_CACHE_MANAGER = "localCacheManager";

    /**
     * 注册 Redis 缓存管理器。
     *
     * @param connectionFactory Redis 连接工厂
     * @param objectMapper JSON 序列化器
     * @param properties 缓存配置
     * @return Redis 缓存管理器
     */
    @Bean(name = {CACHE_MANAGER, REDIS_CACHE_MANAGER})
    @Primary
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = CACHE_MANAGER)
    public @NotNull CacheManager redisCacheManager(
            @NotNull RedisConnectionFactory connectionFactory,
            @NotNull ObjectMapper objectMapper,
            @NotNull Sca4djCacheProperties properties) {
        GenericJackson2JsonRedisSerializer serializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(objectMapper.copy())
                .defaultTyping(true)
                .build();
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.getRedisTtl())
                .disableCachingNullValues()
                .prefixCacheNameWith(properties.getKeyPrefix() + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        CacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .build();
        log.info("Redis 缓存管理器已启用 | ttl={}", properties.getRedisTtl());
        return cacheManager;
    }

    /**
     * 注册本地缓存管理器。
     *
     * @param properties 缓存配置
     * @return 本地缓存管理器
     */
    @Bean(name = LOCAL_CACHE_MANAGER)
    @ConditionalOnMissingBean(name = LOCAL_CACHE_MANAGER)
    public @NotNull CaffeineCacheManager localCacheManager(@NotNull Sca4djCacheProperties properties) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(properties.getLocalTtl())
                .maximumSize(properties.getLocalMaximumSize()));
        log.info(
                "本机缓存管理器已启用 | ttl={} maximumSize={}",
                properties.getLocalTtl(),
                properties.getLocalMaximumSize());
        return cacheManager;
    }
}
