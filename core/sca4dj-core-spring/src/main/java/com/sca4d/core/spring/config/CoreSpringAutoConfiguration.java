package com.sca4d.core.spring.config;

import com.sca4d.core.spring.i18n.I18nProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.redis.RedisIdempotencyProperties;
import com.sca4d.core.spring.redis.RedisPipelineProperties;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 统一导入 Core Spring 的内部 Bean 工厂，保持自动配置条件和排除语义。 */
@AutoConfiguration(after = WebMvcAutoConfiguration.class)
@ConditionalOnClass(WebMvcConfigurer.class)
@EnableAspectJAutoProxy
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@EnableConfigurationProperties({
        CoreSpringProperties.class,
        HttpInputValidationProperties.class,
        IdProperties.class,
        I18nProperties.class,
        RedisIdempotencyProperties.class,
        RedisPipelineProperties.class,
        SqlBatchProperties.class
})
@Import({
        CoreInfrastructureConfiguration.class,
        CoreI18nConfiguration.class,
        CoreCallLifecycleConfiguration.class,
        CoreConcurrencyConfiguration.class,
        CorePermissionConfiguration.class,
        CoreIdentityConfiguration.class,
        CoreWebMvcConfiguration.class
})
public class CoreSpringAutoConfiguration {

    public CoreSpringAutoConfiguration() {
    }
}
