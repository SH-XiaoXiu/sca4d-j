package com.sca4d.core.spring.config;

import com.sca4d.core.api.annotation.Idempotent;
import com.sca4d.core.api.annotation.Locked;
import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.IdempotencyCallInterceptor;
import com.sca4d.core.api.concurrent.IdempotencyFingerprints;
import com.sca4d.core.api.concurrent.IdempotencyStore;
import com.sca4d.core.api.concurrent.LockCallInterceptor;
import com.sca4d.core.api.concurrent.RateLimitCallInterceptor;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.redis.RedisIdempotencyProperties;
import com.sca4d.core.spring.web.MethodArgumentsIdempotencyKeyResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/** 注册限流、幂等和分布式锁的调用参与者。 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class CoreConcurrencyConfiguration {

    /** @param objectMapper 脚手架内部 JSON 映射器 @return 幂等参数规范化和摘要入口 */
    @Bean
    @ConditionalOnMissingBean
    IdempotencyFingerprints idempotencyFingerprints(@Sca4djJson Sca4djMapper objectMapper) {
        return new IdempotencyFingerprints(objectMapper);
    }

    /** @param fingerprints 幂等参数规范化入口 @return 使用方法参数摘要的幂等键解析器 */
    @Bean
    @ConditionalOnMissingBean
    MethodArgumentsIdempotencyKeyResolver methodArgumentsIdempotencyKeyResolver(
            IdempotencyFingerprints fingerprints) {
        return new MethodArgumentsIdempotencyKeyResolver(fingerprints);
    }

    /** @param rateLimiter 共享限流器 @param resolverLookup 调用值解析器目录 @return 限流调用前评估器 */
    @Bean
    RateLimitCallInterceptor rateLimitCallInterceptor(
            RateLimiter rateLimiter,
            CallValueResolverLookup resolverLookup,
            SPICallValueResolver expressionResolver) {
        return new RateLimitCallInterceptor(rateLimiter, resolverLookup, expressionResolver);
    }

    /**
     * @param idempotencyStore 幂等状态和结果存储
     * @param resolverLookup 调用值解析器目录
     * @param idempotencyFingerprints 幂等参数摘要入口
     * @param idempotencyProperties 幂等处理时间配置
     * @return 幂等执行控制器
     */
    @Bean
    IdempotencyCallInterceptor idempotencyCallInterceptor(
            IdempotencyStore idempotencyStore,
            CallValueResolverLookup resolverLookup,
            SPICallValueResolver expressionResolver,
            IdempotencyFingerprints idempotencyFingerprints,
            RedisIdempotencyProperties idempotencyProperties,
            @Qualifier("callIdempotencyScheduler") ScheduledExecutorService renewalScheduler) {
        return new IdempotencyCallInterceptor(
                idempotencyStore,
                resolverLookup,
                expressionResolver,
                idempotencyFingerprints,
                idempotencyProperties.getProcessingTtl(),
                idempotencyProperties.getRetryAfter(),
                idempotencyProperties.getResultRetention(),
                renewalScheduler);
    }

    /** @return 在异步调用结束前按 token 续期幂等处理权的单线程调度器 */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "callIdempotencyScheduler")
    ScheduledExecutorService callIdempotencyScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "sca4dj-call-idempotency-renewal");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /** @param distributedLock 分布式锁 @param resolverLookup 调用值解析器目录 @return 锁执行控制器 */
    @Bean
    LockCallInterceptor lockCallInterceptor(
            DistributedLock distributedLock,
            CallValueResolverLookup resolverLookup,
            SPICallValueResolver expressionResolver) {
        return new LockCallInterceptor(distributedLock, resolverLookup, expressionResolver);
    }

    /** 注册 Core 自己定义的限流注解。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<RateLimited> rateLimitCallBinding() {
        return () -> RateLimited.class;
    }

    /** 注册 Core 自己定义的幂等注解。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<Idempotent> idempotentCallBinding() {
        return () -> Idempotent.class;
    }

    /** 注册 Core 自己定义的锁注解。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<Locked> lockedCallBinding() {
        return () -> Locked.class;
    }
}
