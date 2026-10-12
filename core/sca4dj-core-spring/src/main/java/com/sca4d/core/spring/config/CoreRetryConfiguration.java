package com.sca4d.core.spring.config;

import com.sca4d.core.api.retry.RetryExecutor;
import com.sca4d.core.spring.retry.SpringRetryExecutor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

/** 在类路径包含 Spring Retry 时注册可替换的同步执行器。 */
@ConditionalOnClass(name = "org.springframework.retry.support.RetryTemplate")
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class CoreRetryConfiguration {
    /** @return 不自动包装 Call 和事务的重试执行器 */
    @Bean
    @ConditionalOnMissingBean(RetryExecutor.class)
    RetryExecutor retryExecutor() {
        return new SpringRetryExecutor();
    }
}
