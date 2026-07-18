package com.sca4d.config;

import com.sca4d.common.auth.Authentication;
import com.sca4d.common.i18n.LocaleResponseInterceptor;
import com.sca4d.common.request.web.CurrentRequestFilter;
import com.sca4d.common.web.ClientStateFilter;
import com.sca4d.config.properties.Sca4djRequestLogProperties;
import com.sca4d.config.properties.Sca4djRequestProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 */
@Configuration
@EnableConfigurationProperties({Sca4djRequestProperties.class, Sca4djRequestLogProperties.class})
public class WebMvcConfig implements WebMvcConfigurer {

    private static final int CLIENT_STATE_FILTER_ORDER = Integer.MIN_VALUE + 500;

    /**
     * 注册响应内容语言拦截器。
     *
     * @param registry Spring MVC 拦截器注册表
     */
    @Override
    public void addInterceptors(@NotNull InterceptorRegistry registry) {
        registry.addInterceptor(new LocaleResponseInterceptor());
    }

    /**
     * 注册当前请求过滤器。
     *
     * @param authentication 读取认证会话的实现
     * @param requestLogProperties 请求日志配置
     * @return 请求上下文过滤器
     */
    @Bean("sca4dCurrentRequestFilter")
    @ConditionalOnProperty(prefix = "sca4dj.request", name = "context-enabled", havingValue = "true", matchIfMissing = true)
    public @NotNull CurrentRequestFilter sca4dCurrentRequestFilter(
            @NotNull Authentication authentication,
            @NotNull Sca4djRequestLogProperties requestLogProperties) {
        return new CurrentRequestFilter(authentication, requestLogProperties);
    }

    /**
     * 注册当前请求过滤器。
     *
     * @param currentRequestFilter 当前请求过滤器
     * @param properties 请求配置
     * @return Filter 注册 Bean
     */
    @Bean("sca4dCurrentRequestFilterRegistration")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sca4dj.request", name = "context-enabled", havingValue = "true", matchIfMissing = true)
    public @NotNull FilterRegistrationBean<CurrentRequestFilter> currentRequestFilterRegistration(
            @NotNull CurrentRequestFilter currentRequestFilter, @NotNull Sca4djRequestProperties properties) {
        // 显式注册共用请求态过滤器，以便在日志和认证过滤器之前建立 CurrentRequest。
        FilterRegistrationBean<CurrentRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(currentRequestFilter);
        registration.addUrlPatterns("/*");
        // 过滤器顺序由配置共用控制，调用方不需要依赖 Spring 容器的注册顺序。
        registration.setOrder(properties.getContextFilterOrder());
        return registration;
    }

    /**
     * 注册前端本地状态响应头过滤器。
     *
     * @return 前端本地状态响应头过滤器
     */
    @Bean
    @ConditionalOnMissingBean(ClientStateFilter.class)
    public @NotNull ClientStateFilter clientStateFilter() {
        return new ClientStateFilter();
    }

    /**
     * 注册前端本地状态响应头过滤器。
     *
     * @param clientStateFilter 前端本地状态响应头过滤器
     * @return Filter 注册 Bean
     */
    @Bean("clientStateFilterRegistration")
    @ConditionalOnMissingBean(name = "clientStateFilterRegistration")
    public @NotNull FilterRegistrationBean<ClientStateFilter> clientStateFilterRegistration(
            @NotNull ClientStateFilter clientStateFilter) {
        FilterRegistrationBean<ClientStateFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(clientStateFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(CLIENT_STATE_FILTER_ORDER);
        return registration;
    }
}
