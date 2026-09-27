package com.sca4d.core.spring.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.gateway.GatewayCallback;
import com.sca4d.core.api.runtime.InstanceCapabilities;
import com.sca4d.core.spring.config.InstanceProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** 把 Core Gateway 回调契约、可信转发信息和 Server 租约注册接入 Spring MVC。 */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@EnableConfigurationProperties(GatewayRegistrationProperties.class)
public class GatewaySpringAutoConfiguration {

    /**
     * 创建回调 HTTP 分发器。
     *
     * @param callbacks 应用声明的回调 Bean
     * @param objectMapper HTTP JSON 映射器
     * @return 回调 HTTP 分发器
     */
    @Bean
    @ConditionalOnMissingBean
    @NotNull
    GatewayCallbackDispatcher gatewayCallbackDispatcher(
            @NotNull List<@NotNull GatewayCallback<?, ?>> callbacks,
            @NotNull ObjectMapper objectMapper) {
        return new GatewayCallbackDispatcher(callbacks, objectMapper);
    }

    /**
     * 创建在审计过滤器前恢复可信外部路径和客户端地址的过滤器。
     *
     * @param properties Gateway 注册配置
     * @return 最高优先级的 Servlet 过滤器注册
     * @throws IllegalArgumentException 启用注册但没有配置控制 Secret 时抛出
     */
    @Bean
    @ConditionalOnProperty(prefix = "sca4dj.gateway.registration", name = "enabled", havingValue = "true")
    @NotNull
    FilterRegistrationBean<TrustedGatewayRequestFilter> trustedGatewayRequestFilter(
            @NotNull GatewayRegistrationProperties properties) {
        FilterRegistrationBean<TrustedGatewayRequestFilter> registration = new FilterRegistrationBean<>(
                new TrustedGatewayRequestFilter(properties.getSecret()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * 创建 readiness 后的实例租约注册生命周期。
     *
     * @param properties Gateway 注册配置
     * @param callbacks 当前实例声明的全部回调
     * @return Server 实例注册、续租和注销生命周期
     * @throws IllegalStateException Gateway 注册配置不合法时抛出
     */
    @Bean
    @ConditionalOnProperty(prefix = "sca4dj.gateway.registration", name = "enabled", havingValue = "true")
    @NotNull
    GatewayRegistrationLifecycle gatewayRegistrationLifecycle(
            @NotNull GatewayRegistrationProperties properties,
            @NotNull List<@NotNull GatewayCallback<?, ?>> callbacks,
            @NotNull InstanceProperties instanceProperties,
            @NotNull InstanceCapabilities capabilities) {
        return new GatewayRegistrationLifecycle(properties, callbacks, instanceProperties, capabilities);
    }
}
