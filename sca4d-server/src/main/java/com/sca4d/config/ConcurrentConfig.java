package com.sca4d.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.concurrent.SpelKeyEvaluator;
import com.sca4d.common.concurrent.idempotency.IdempotencyAspect;
import com.sca4d.common.concurrent.idempotency.IdempotencyStore;
import com.sca4d.common.concurrent.idempotency.RedisIdempotencyStore;
import com.sca4d.common.concurrent.lock.DistributedLockAspect;
import com.sca4d.common.concurrent.lock.DistributedLockExecutor;
import com.sca4d.common.concurrent.lock.DistributedLockTemplate;
import com.sca4d.common.concurrent.lock.RedissonDistributedLockExecutor;
import com.sca4d.common.concurrent.ratelimit.Bucket4jRedisRateLimiter;
import com.sca4d.common.concurrent.ratelimit.RateLimitAspect;
import com.sca4d.common.concurrent.ratelimit.RateLimitKeyGenerator;
import com.sca4d.common.concurrent.ratelimit.RateLimiter;
import com.sca4d.common.redis.RedisScriptExecutor;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import io.github.bucket4j.distributed.expiration.BasedOnTimeForRefillingBucketUpToMaxExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配共用 SpEL key、Redis 幂等、Redisson 锁和 Bucket4j 集群限流公共组件。 */
@Configuration
@EnableConfigurationProperties(Sca4djConcurrentProperties.class)
public class ConcurrentConfig {

    /**
     * 提供并发注解共同使用的方法参数 SpEL 解析器。
     *
     * @return 支持 Spring 参数名发现和表达式求值失败收敛的解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull SpelKeyEvaluator spelKeyEvaluator() {
        return new SpelKeyEvaluator();
    }

    /**
     * 创建使用 Lua token 条件推进的 Redis 幂等记录存储。
     *
     * @param scripts Redis Lua 统一执行边界
     * @param objectMapper 项目全局 JSON 契约
     * @return 支持处理中租约和成功响应重放的存储
    */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull IdempotencyStore idempotencyStore(
            @NotNull RedisScriptExecutor scripts, @NotNull ObjectMapper objectMapper) {
        return new RedisIdempotencyStore(scripts, objectMapper);
    }

    /**
     * 创建绑定固定 operation、请求指纹和 HTTP 重放标记的幂等切面。
     *
     * @param store 集群共享幂等记录存储
     * @param objectMapper 请求指纹和响应重放使用的全局 JSON 映射器
     * @param keyEvaluator 显式 key 使用的共用 SpEL 解析器
     * @param properties 处理租约、成功窗口和重试配置
     * @param responseProvider 可选 HTTP 响应来源
     * @return 业务结果与 Redis 落档故障隔离的幂等切面
    */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull IdempotencyAspect idempotencyAspect(
            @NotNull IdempotencyStore store,
            @NotNull ObjectMapper objectMapper,
            @NotNull SpelKeyEvaluator keyEvaluator,
            @NotNull Sca4djConcurrentProperties properties,
            @NotNull ObjectProvider<HttpServletResponse> responseProvider) {
        return new IdempotencyAspect(store, objectMapper, keyEvaluator, properties, responseProvider);
    }

    /**
     * 创建默认使用 Redisson watchdog 的集群锁执行器。
     *
     * @param redissonClient 集群共享 Redisson 客户端
     * @return 仅由当前线程所有者执行和释放锁的执行器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull DistributedLockExecutor distributedLockExecutor(@NotNull RedissonClient redissonClient) {
        return new RedissonDistributedLockExecutor(redissonClient);
    }

    /**
     * 创建供锁注解切面和业务代码直接调用的锁模板。
     *
     * @param executor Redisson 所有权执行器
     * @param properties 默认等待和租约策略
     * @return 共用锁执行入口
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull DistributedLockTemplate distributedLockTemplate(
            @NotNull DistributedLockExecutor executor, @NotNull Sca4djConcurrentProperties properties) {
        return new DistributedLockTemplate(executor, properties);
    }

    /**
     * 创建读取方法或类型锁注解并执行集群锁的切面。
     *
     * @param lockTemplate 共用锁执行入口
     * @param keyEvaluator 锁资源 key 使用的 SpEL 解析器
     * @param properties 锁开关、前缀和默认时长
     * @return 在业务事务外持有锁的切面
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull DistributedLockAspect distributedLockAspect(
            @NotNull DistributedLockTemplate lockTemplate,
            @NotNull SpelKeyEvaluator keyEvaluator,
            @NotNull Sca4djConcurrentProperties properties) {
        return new DistributedLockAspect(lockTemplate, keyEvaluator, properties);
    }

    /**
     * 创建使用 Redisson CAS 和按补满时间回收状态的 Bucket4j 代理管理器。
     *
     * @param redissonClient 必须是暴露 CommandExecutor 的 Redisson 官方实现
     * @param properties Bucket 状态补满后的额外保留时间
     * @return 所有节点共享令牌桶状态的代理管理器
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public @NotNull ProxyManager<String> bucket4jProxyManager(
            @NotNull RedissonClient redissonClient, @NotNull Sca4djConcurrentProperties properties) {
        if (!(redissonClient instanceof Redisson redisson)) {
            throw new IllegalStateException("Bucket4j Redisson 限流需要 org.redisson.Redisson 客户端实现");
        }
        return Bucket4jRedisson.casBasedBuilder(redisson.getCommandExecutor())
                .expirationAfterWrite(new BasedOnTimeForRefillingBucketUpToMaxExpirationAfterWriteStrategy(
                        properties.getRateLimit().getKeepAfterRefill()))
                .build();
    }

    /**
     * 创建基于 Redis 代理桶原子消费令牌的限流器。
     *
     * @param proxyManager 已配置 CAS 与过期策略的代理管理器
     * @return 支持规则版本隐式替换的集群限流器
     */
    @Bean
    @ConditionalOnBean(ProxyManager.class)
    @ConditionalOnMissingBean
    public @NotNull RateLimiter rateLimiter(@NotNull ProxyManager<String> proxyManager) {
        return new Bucket4jRedisRateLimiter(proxyManager);
    }

    /**
     * 创建不暴露原始请求维度值的限流 key 生成器。
     *
     * @param properties Redis key 前缀配置
     * @return 使用 SHA-256 维度摘要的生成器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull RateLimitKeyGenerator rateLimitKeyGenerator(@NotNull Sca4djConcurrentProperties properties) {
        return new RateLimitKeyGenerator(properties);
    }

    /**
     * 创建按固定顺序消费全部声明维度的限流切面。
     *
     * @param rateLimiter 消费集群共享令牌桶的实现
     * @param keyGenerator 路由模板和维度摘要生成器
     * @param keyEvaluator CUSTOM 维度使用的 SpEL 解析器
     * @param properties 全局默认令牌桶规则
     * @param requestProvider 可选 Servlet 请求来源
     * @return 使用最长 Retry-After 拒绝请求的限流切面
     */
    @Bean
    @ConditionalOnBean(RateLimiter.class)
    @ConditionalOnMissingBean
    public @NotNull RateLimitAspect rateLimitAspect(
            @NotNull RateLimiter rateLimiter,
            @NotNull RateLimitKeyGenerator keyGenerator,
            @NotNull SpelKeyEvaluator keyEvaluator,
            @NotNull Sca4djConcurrentProperties properties,
            @NotNull ObjectProvider<HttpServletRequest> requestProvider) {
        return new RateLimitAspect(rateLimiter, keyGenerator, keyEvaluator, properties, requestProvider);
    }
}
