package com.sca4d.core.spring.config;

import com.sca4d.core.api.runtime.RuntimeNamespace;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** 从 Spring 应用技术名称创建共享基础设施使用的运行时命名空间。 */
@AutoConfiguration
public class RuntimeNamespaceAutoConfiguration {

    /** 创建运行时命名空间自动配置。 */
    public RuntimeNamespaceAutoConfiguration() {
    }

    /**
     * 从 {@code spring.application.name} 创建应用运行时命名空间。
     *
     * @param environment Spring 配置环境
     * @return 使用应用技术 ID 的运行时命名空间
     * @throws IllegalStateException 应用没有配置 {@code spring.application.name} 时抛出
     */
    @Bean
    RuntimeNamespace runtimeNamespace(Environment environment) {
        String applicationId = environment.getProperty("spring.application.name");
        if (applicationId == null) {
            throw new IllegalStateException("spring.application.name 必须配置为应用技术 ID");
        }
        return new RuntimeNamespace(applicationId);
    }
}
