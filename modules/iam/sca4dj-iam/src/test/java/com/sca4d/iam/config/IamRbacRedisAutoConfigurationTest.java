package com.sca4d.iam.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.redis.Sca4djRedis;
import com.sca4d.iam.rbac.PermissionSelectionRegistry;
import com.sca4d.iam.rbac.PermissionSelections;
import com.sca4d.iam.rbac.RbacDomain;
import com.sca4d.iam.rbac.RbacPermissionCache;
import com.sca4d.iam.rbac.RedisRbacPermissionCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

/** 验证 Redis RBAC 缓存自动配置取得运行命名空间、JSON 规则和权限目录。 */
class IamRbacRedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IamRbacRedisAutoConfiguration.class))
            .withUserConfiguration(TestInfrastructure.class);

    @Test
    @DisplayName("Registers Catalog-Aware Redis Permission Cache")
    void registersCatalogAwareRedisPermissionCache() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RbacPermissionCache.class);
            assertThat(context.getBean(RbacPermissionCache.class))
                    .isInstanceOf(RedisRbacPermissionCache.class);
        });
    }

    /** 提供自动配置条件要求的测试基础设施 Bean。 */
    @Configuration(proxyBeanMethods = false)
    static class TestInfrastructure {

        /** @return 测试使用的脚手架 Redis 模板 */
        @Bean
        @Sca4djRedis
        StringRedisTemplate redis() {
            return mock(StringRedisTemplate.class);
        }

        /** @return 测试应用运行命名空间 */
        @Bean
        RuntimeNamespace runtimeNamespace() {
            return new RuntimeNamespace("example-app");
        }

        /** @return 测试使用的脚手架 JSON 映射器 */
        @Bean
        @Sca4djJson
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        /** @return 包含一个 Platform Operation 的权限选择规则 */
        @Bean
        PermissionSelectionRegistry permissionSelectionRegistry() {
            RbacDomain domain = new RbacDomain("PLATFORM");
            PermissionSelectionRegistry registry = mock(PermissionSelectionRegistry.class);
            when(registry.domains()).thenReturn(Set.of(domain));
            when(registry.selection(domain))
                    .thenReturn(
                            new PermissionSelections(
                                    domain,
                                    List.of(new Operation("platform:roles:read"))));
            return registry;
        }
    }
}
