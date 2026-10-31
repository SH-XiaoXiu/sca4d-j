package com.sca4d.openapi.web;

import com.sca4d.auth.hmac.HmacAuthenticationMethod;
import com.sca4d.auth.session.SessionService;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import com.sca4d.open.auth.OpenAuthenticationService;
import com.sca4d.open.client.OpenClientOperationChecker;
import com.sca4d.open.client.OpenClientService;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 自动装配 OpenAPI 路由、HTTP HMAC 和 OpenAPI Operation 检查。 */
@AutoConfiguration
@ConditionalOnClass(RequestMappingHandlerMapping.class)
public class OpenApiAutoConfiguration {

    /** 缓存 OpenAPI 请求体，确保 HMAC 校验不会消耗 Controller 的输入流。 */
    @Bean
    FilterRegistrationBean<OpenApiRequestBodyFilter> openApiRequestBodyFilter() {
        FilterRegistrationBean<OpenApiRequestBodyFilter> registration = new FilterRegistrationBean<>(
                new OpenApiRequestBodyFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }

    /** 创建并发布应用登记的 OpenAPI 路由目录。 */
    @Bean
    OpenApiRouteCatalog openApiRouteCatalog(
            RequestMappingHandlerMapping mappings,
            OperationCatalog operations,
            org.springframework.beans.factory.ObjectProvider<OpenApiRouteRegistrar> registrars) {
        return new OpenApiRouteCatalog(mappings, operations, registrars);
    }

    /** 注册启动期的 `@OpenApiRoute` 适配器；运行时组件只读取路由目录。 */
    @Bean
    OpenApiRouteRegistrar openApiRouteAnnotationRegistrar(ApplicationContext context) {
        return new OpenApiRouteAnnotationRegistrar(context);
    }

    /** 创建在通用认证拦截器前恢复开放 Bearer 或验证 OpenAPI HMAC 的拦截器。 */
    @Bean
    OpenApiAuthenticationInterceptor openApiAuthenticationInterceptor(
            OpenAuthenticationService authentication,
            OpenClientService clients,
            HmacAuthenticationMethod hmac,
            SessionService sessions,
            OpenApiRouteCatalog routes) {
        return new OpenApiAuthenticationInterceptor(authentication, clients, hmac, sessions, routes);
    }

    /** 让仅通过路由目录登记的开放 Controller 方法进入 Call 生命周期。 */
    @Bean
    CallLifecycleAdvisor.MethodBinding openApiRouteCallBinding(OpenApiRouteCatalog routes) {
        return new OpenApiRouteCallBinding(routes);
    }

    /** 在身份恢复后、业务权限检查前验证 Open Client 的 Operation 权限。 */
    @Bean
    CallLifecycle.Participant openApiOperationCallInterceptor(
            OpenApiRouteCatalog routes,
            OpenClientOperationChecker checker) {
        return new OpenApiOperationCallInterceptor(routes, checker);
    }

    /** 将开放 API 认证放入 HTTP 层，Operation 检查由 Call 生命周期完成。 */
    @Bean
    WebMvcConfigurer openApiWebMvcConfigurer(OpenApiAuthenticationInterceptor authentication) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NotNull InterceptorRegistry registry) {
                registry.addInterceptor(authentication)
                        .order(Ordered.HIGHEST_PRECEDENCE + 50);
            }
        };
    }
}
