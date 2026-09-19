package com.sca4d.core.spring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.IdempotencyStore;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.redis.RedisDistributedLock;
import com.sca4d.core.spring.redis.RedisIdempotencyProperties;
import com.sca4d.core.spring.redis.RedisIdempotencyStore;
import com.sca4d.core.spring.redis.RedisRateLimitProperties;
import com.sca4d.core.spring.redis.RedisRateLimiter;
import com.sca4d.core.spring.redis.Sca4djRedis;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import org.jetbrains.annotations.NotNull;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 使用应用默认 Redis 连接创建脚手架 Redis 模板、共享限流和分布式锁。 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass({RedisConnectionFactory.class, StringRedisTemplate.class})
@ConditionalOnBean(RedisConnectionFactory.class)
@Import(CoreRedisAutoConfiguration.RedisConcurrencyConfiguration.class)
public class CoreRedisAutoConfiguration {

    /** 创建 Core Redis 自动配置。 */
    public CoreRedisAutoConfiguration() {
    }

    /**
     * 使用应用唯一或默认的 Redis 连接工厂创建脚手架内部字符串操作模板。
     *
     * @param connectionFactory 应用默认基础设施 Redis 连接工厂
     * @return 使用字符串 key 和 value 序列化的脚手架 Redis 模板
     */
    @Bean(name = "sca4djStringRedisTemplate", defaultCandidate = false)
    @Sca4djRedis
    @ConditionalOnMissingBean(annotation = Sca4djRedis.class)
    public @NotNull StringRedisTemplate sca4djStringRedisTemplate(
            @NotNull RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /** 在 Spring Boot 已提供 Redis 连接信息时安装跨实例并发实现。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Redisson.class)
    @ConditionalOnBean(RedisConnectionDetails.class)
    @EnableConfigurationProperties({RedisRateLimitProperties.class, RedisIdempotencyProperties.class})
    static class RedisConcurrencyConfiguration {

        /**
         * 创建独立的 Redisson 客户端，不替换 Spring Data Redis 连接工厂和 Template。
         *
         * @param connectionDetails Spring Boot Redis 连接信息
         * @return Redisson 客户端
         */
        @Bean(destroyMethod = "shutdown")
        @ConditionalOnMissingBean(RedissonClient.class)
        Redisson redisson(@NotNull RedisConnectionDetails connectionDetails) {
            return (Redisson) Redisson.create(RedissonConfigFactory.create(connectionDetails));
        }

        /**
         * 创建按额度恢复时间自动过期的 Bucket4j Redis CAS 代理。
         *
         * @param redisson 当前 Redisson 客户端
         * @param properties 限流状态保留配置
         * @return 共享令牌桶代理
         */
        @Bean
        @ConditionalOnMissingBean(ProxyManager.class)
        ProxyManager<String> bucket4jProxyManager(
                @NotNull Redisson redisson,
                @NotNull RedisRateLimitProperties properties) {
            return Bucket4jRedisson.casBasedBuilder(redisson.getCommandExecutor())
                    .expirationAfterWrite(
                            ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                    properties.getKeepAfterRefill()))
                    .keyMapper(Mapper.STRING)
                    .build();
        }

        /**
         * 创建多个应用实例共享的令牌桶限流器。
         *
         * @param proxyManager Bucket4j Redis CAS 代理
         * @param runtimeNamespace 当前应用的运行时命名空间
         * @return Redis 限流器
         */
        @Bean
        @ConditionalOnMissingBean(RateLimiter.class)
        RateLimiter redisRateLimiter(
                @NotNull ProxyManager<String> proxyManager,
                @NotNull RuntimeNamespace runtimeNamespace) {
            return new RedisRateLimiter(proxyManager, runtimeNamespace);
        }

        /**
         * 创建多个应用实例共享的 Redis 幂等存储。
         *
         * @param redis 脚手架内部字符串 Redis 模板
         * @param runtimeNamespace 当前应用的运行时命名空间
         * @param objectMapper 脚手架内部 JSON 映射器
         * @return Redis 幂等状态机
         */
        @Bean
        @ConditionalOnMissingBean(IdempotencyStore.class)
        IdempotencyStore redisIdempotencyStore(
                @Sca4djRedis @NotNull StringRedisTemplate redis,
                @NotNull RuntimeNamespace runtimeNamespace,
                @Sca4djJson @NotNull ObjectMapper objectMapper) {
            return new RedisIdempotencyStore(redis, runtimeNamespace, objectMapper);
        }

        /**
         * 创建多个应用实例共享的 Redisson 锁。
         *
         * @param redisson Redisson 客户端
         * @param runtimeNamespace 当前应用的运行时命名空间
         * @return Redis 分布式锁
         */
        @Bean
        @ConditionalOnMissingBean(DistributedLock.class)
        DistributedLock redisDistributedLock(
                @NotNull RedissonClient redisson,
                @NotNull RuntimeNamespace runtimeNamespace) {
            return new RedisDistributedLock(redisson, runtimeNamespace);
        }
    }
}
