package com.sca4d.core.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.IdempotencyStore;
import com.sca4d.core.api.concurrent.RateLimitDecision;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.spring.redis.RedisDistributedLock;
import com.sca4d.core.spring.redis.RedisIdempotencyStore;
import com.sca4d.core.spring.redis.Sca4djRedis;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.util.List;

/** 验证脚手架 Redis 模板只使用应用默认连接工厂且不参与普通类型注入。 */
class CoreRedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.application.name=sca4d-test")
            .withConfiguration(
                    AutoConfigurations.of(
                            CoreJacksonAutoConfiguration.class,
                            AppIsolationAutoConfiguration.class,
                            JacksonAutoConfiguration.class,
                            CoreRedisAutoConfiguration.class));

    @Test
    @DisplayName("Creates non-default scaffold template from default connection factory")
    void createsNonDefaultScaffoldTemplateFromDefaultConnectionFactory() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        StringRedisTemplate businessTemplate = new StringRedisTemplate(connectionFactory);

        contextRunner
                .withBean("redisConnectionFactory", RedisConnectionFactory.class, () -> connectionFactory)
                .withBean(
                        "businessStringRedisTemplate",
                        StringRedisTemplate.class,
                        () -> businessTemplate,
                        definition -> definition.setPrimary(true))
                .run(context -> {
                    RedisTemplate<?, ?> sca4djTemplate = context.getBean(
                            "sca4djRedisTemplate",
                            RedisTemplate.class);
                    AbstractBeanDefinition definition = (AbstractBeanDefinition) context.getBeanFactory()
                            .getBeanDefinition("sca4djRedisTemplate");

                    assertThat(sca4djTemplate.getConnectionFactory()).isSameAs(connectionFactory);
                    assertThat(context.getBean(StringRedisTemplate.class)).isSameAs(businessTemplate);
                    assertThat(definition.isDefaultCandidate()).isFalse();
                });
    }

    @Test
    @DisplayName("Uses application template qualified for scaffold Redis")
    void usesApplicationTemplateQualifiedForScaffoldRedis() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withBean("redisConnectionFactory", RedisConnectionFactory.class, () -> connectionFactory)
                .withUserConfiguration(CustomSca4djRedisConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean("sca4djRedisTemplate");
                    assertThat(context.getBean("customSca4djRedisTemplate", RedisTemplate.class))
                            .isSameAs(
                                    context.getBeansWithAnnotation(Sca4djRedis.class)
                                            .get("customSca4djRedisTemplate"));
                });
    }

    @Test
    @DisplayName("Uses primary connection factory when multiple factories exist")
    void usesPrimaryConnectionFactoryWhenMultipleFactoriesExist() {
        RedisConnectionFactory primary = mock(RedisConnectionFactory.class);
        RedisConnectionFactory secondary = mock(RedisConnectionFactory.class);

        contextRunner
                .withBean(
                        "primaryRedisConnectionFactory",
                        RedisConnectionFactory.class,
                        () -> primary,
                        definition -> definition.setPrimary(true))
                .withBean("secondaryRedisConnectionFactory", RedisConnectionFactory.class, () -> secondary)
                .run(
                        context -> assertThat(
                                context.getBean(
                                        "sca4djRedisTemplate",
                                        RedisTemplate.class)
                                        .getConnectionFactory())
                                .isSameAs(primary));
    }

    @Test
    @DisplayName("Fails when multiple connection factories have no default")
    void failsWhenMultipleConnectionFactoriesHaveNoDefault() {
        contextRunner
                .withBean(
                        "firstRedisConnectionFactory",
                        RedisConnectionFactory.class,
                        () -> mock(RedisConnectionFactory.class))
                .withBean(
                        "secondRedisConnectionFactory",
                        RedisConnectionFactory.class,
                        () -> mock(RedisConnectionFactory.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class);
                });
    }

    @Test
    @DisplayName("Installs Redis concurrency implementations when connection details exist")
    @SuppressWarnings("unchecked")
    void installsRedisConcurrencyImplementationsWhenConnectionDetailsExist() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        RateLimiter customRateLimiter = (key, limit) -> RateLimitDecision.allow();

        contextRunner
                .withBean("redisConnectionFactory", RedisConnectionFactory.class, () -> connectionFactory)
                .withBean(RedisConnectionDetails.class, () -> mock(RedisConnectionDetails.class))
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .withBean("bucket4jProxyManager", ProxyManager.class, () -> proxyManager)
                .withBean(RateLimiter.class, () -> customRateLimiter)
                .withPropertyValues("sca4dj.serialization.key-prefix=TEST_APP")
                .withBean(Clock.class, Clock::systemUTC)
                .run(context -> {
                    assertThat(context.getBean(IdempotencyStore.class))
                            .isInstanceOf(RedisIdempotencyStore.class);
                    assertThat(context.getBean(DistributedLock.class))
                            .isInstanceOf(RedisDistributedLock.class);
                    assertThat(context.getBean(RateLimiter.class)).isSameAs(customRateLimiter);
                });
    }

    /** 声明替换脚手架 Redis 模板的应用测试配置。 */
    @Configuration(proxyBeanMethods = false)
    static class CustomSca4djRedisConfiguration {

        /** @return 应用显式声明的脚手架 Redis 模板 */
        @Bean
        @Sca4djRedis
        RedisTemplate<Key, String> customSca4djRedisTemplate() {
            return new CoreRedisAutoConfiguration().sca4djRedisTemplate(
                    mock(RedisConnectionFactory.class),
                    List.of());
        }
    }
}
