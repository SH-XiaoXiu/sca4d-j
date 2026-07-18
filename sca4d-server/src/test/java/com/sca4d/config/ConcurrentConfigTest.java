package com.sca4d.config;

import static java.time.Duration.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.concurrent.SpelKeyEvaluator;
import com.sca4d.common.concurrent.idempotency.IdempotencyAspect;
import com.sca4d.common.concurrent.idempotency.IdempotencyStore;
import com.sca4d.common.concurrent.lock.DistributedLockAspect;
import com.sca4d.common.concurrent.lock.DistributedLockExecutor;
import com.sca4d.common.concurrent.lock.DistributedLockTemplate;
import com.sca4d.common.concurrent.ratelimit.RateLimitKeyGenerator;
import com.sca4d.common.redis.RedisScriptExecutor;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 验证分布式锁、幂等和限流组件的 Spring 装配。 */
class ConcurrentConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(RedisScriptExecutor.class, () -> mock(RedisScriptExecutor.class))
            .withBean(DistributedLockExecutor.class, () -> mock(DistributedLockExecutor.class))
            .withUserConfiguration(ConcurrentConfig.class);

    @Test
    void shouldRegisterCommonConcurrentComponents() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Sca4djConcurrentProperties.class);
            assertThat(context).hasSingleBean(SpelKeyEvaluator.class);
            assertThat(context).hasSingleBean(RateLimitKeyGenerator.class);
            assertThat(context).hasSingleBean(IdempotencyStore.class);
            assertThat(context).hasSingleBean(IdempotencyAspect.class);
            assertThat(context).hasSingleBean(DistributedLockExecutor.class);
            assertThat(context).hasSingleBean(DistributedLockTemplate.class);
            assertThat(context).hasSingleBean(DistributedLockAspect.class);
        });
    }

    @Test
    void shouldExposeSafeConcurrentDefaults() {
        contextRunner.run(context -> {
            Sca4djConcurrentProperties properties = context.getBean(Sca4djConcurrentProperties.class);

            assertThat(properties.getIdempotency().isEnabled()).isTrue();
            assertThat(properties.getIdempotency().getKeyPrefix()).isEqualTo("idempotency");
            assertThat(properties.getIdempotency().getProcessingTtl()).isPositive();
            assertThat(properties.getIdempotency().getSuccessTtl()).isPositive();
            assertThat(properties.getIdempotency().getRetryAfter()).isPositive();
            assertThat(properties.getLock().getLeaseTime()).isZero();
            assertThat(properties.getLock().getWaitTime()).isGreaterThanOrEqualTo(ZERO);
            assertThat(properties.getRateLimit().getCapacity()).isPositive();
            assertThat(properties.getRateLimit().getRefillTokens()).isPositive();
            assertThat(properties.getRateLimit().getTimeWindow()).isPositive();
        });
    }
}
