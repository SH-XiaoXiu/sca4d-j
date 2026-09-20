package com.sca4d.openapi.web;

import com.sca4d.auth.hmac.HmacAuthenticationMethod;
import com.sca4d.auth.session.SessionService;
import com.sca4d.auth.session.codec.SessionCodec;
import com.sca4d.auth.sharedsecret.SharedSecretAuthenticationMethod;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.openapi.client.OpenApiClientOperationCheckerImpl;
import com.sca4d.openapi.client.OpenApiClientRepository;
import com.sca4d.openapi.client.OpenApiClientService;
import com.sca4d.openapi.client.mapper.OpenApiClientMapper;
import com.sca4d.openapi.config.OpenApiProperties;
import com.sca4d.openapi.oauth.OpenApiAuthenticationEndpoint;
import com.sca4d.openapi.oauth.OpenApiJwtTokenService;
import com.sca4d.openapi.oauth.OpenApiTokenEndpointHandler;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Clock;

/** 自动装配 OpenAPI 路由校验、认证端点和 Client Operation 请求检查。 */
@AutoConfiguration
@MapperScan(basePackageClasses = OpenApiClientMapper.class)
@ConditionalOnClass(RequestMappingHandlerMapping.class)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiAutoConfiguration {

    /** 创建独立 OpenAPI JWT 签发和恢复服务。 */
    @Bean
    OpenApiJwtTokenService openApiJwtTokenService(
            SessionCodec codec,
            OpenApiProperties properties,
            Clock clock) {
        return new OpenApiJwtTokenService(codec, properties, clock);
    }

    /** 缓存 OpenAPI 请求体，确保 HMAC 校验不会消耗 Controller 的输入流。 */
    @Bean
    FilterRegistrationBean<OpenApiRequestBodyFilter> openApiRequestBodyFilter() {
        FilterRegistrationBean<OpenApiRequestBodyFilter> registration = new FilterRegistrationBean<>(
                new OpenApiRequestBodyFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }

    /** 创建应用声明的 OpenAPI 路由目录。 */
    @Bean
    OpenApiRouteCatalog openApiRouteCatalog(
            ApplicationContext context,
            OperationCatalog operations) {
        return new OpenApiRouteCatalog(context, operations);
    }

    /** 创建模块实现的 Client Credentials Token Handler。 */
    @Bean
    @ConditionalOnBean(OpenApiAuthenticationEndpoint.class)
    OpenApiTokenEndpointHandler openApiTokenEndpointHandler(
            OpenApiClientService clients,
            IdentityService identities,
            SharedSecretAuthenticationMethod sharedSecrets,
            SessionService sessions,
            OpenApiJwtTokenService jwtTokens) {
        return new OpenApiTokenEndpointHandler(
                clients,
                identities,
                sharedSecrets,
                sessions,
                jwtTokens);
    }

    /** 按具体应用提供的 Bean 注册 OpenAPI 认证端点。 */
    @Bean
    @ConditionalOnBean(OpenApiAuthenticationEndpoint.class)
    OpenApiAuthenticationEndpointRegistrar openApiAuthenticationEndpointRegistrar(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
            OpenApiAuthenticationEndpoint endpoint,
            OpenApiTokenEndpointHandler handler) {
        return new OpenApiAuthenticationEndpointRegistrar(mappings, endpoint, handler);
    }

    /** 创建 OpenAPI Client 持久化边界。 */
    @Bean
    OpenApiClientRepository openApiClientRepository(OpenApiClientMapper mapper, SqlBatches sqlBatches) {
        return new OpenApiClientRepository(mapper, sqlBatches);
    }

    /** 创建 OpenAPI Client 应用服务。 */
    @Bean
    OpenApiClientService openApiClientService(
            IdentityService identities,
            OperationCatalog operations,
            OpenApiRouteCatalog routes,
            OpenApiClientRepository clients,
            SharedSecretAuthenticationMethod sharedSecrets,
            HmacAuthenticationMethod hmac) {
        return new OpenApiClientService(
                identities,
                operations,
                routes,
                clients,
                sharedSecrets,
                hmac);
    }

    /** 使用持久化 Client 服务替换默认拒绝实现。 */
    @Bean
    @ConditionalOnMissingBean(OpenApiClientOperationChecker.class)
    OpenApiClientOperationChecker openApiClientOperationChecker(OpenApiClientService clients) {
        return new OpenApiClientOperationCheckerImpl(clients);
    }

    /** 没有持久化 Client 实现时拒绝所有 OpenAPI 请求，避免模块误配置后放行调用。 */
    @Bean
    @ConditionalOnMissingBean
    OpenApiClientOperationChecker unavailableOpenApiClientOperationChecker() {
        return (call, operation) -> {
            throw new IllegalStateException("当前应用没有 OpenAPI Client Operation 检查实现");
        };
    }

    /** 创建在通用认证拦截器前处理 OpenAPI JWT 和 HMAC 的拦截器。 */
    @Bean
    OpenApiAuthenticationInterceptor openApiAuthenticationInterceptor(
            OpenApiJwtTokenService jwtTokens,
            OpenApiClientService clients,
            HmacAuthenticationMethod hmac,
            SessionService sessions) {
        return new OpenApiAuthenticationInterceptor(jwtTokens, clients, hmac, sessions);
    }

    /** 将 OpenAPI Operation 检查放在普通认证拦截器之后、Controller AOP 之前。 */
    @Bean
    WebMvcConfigurer openApiWebMvcConfigurer(
            OpenApiAuthenticationInterceptor authentication,
            OpenApiClientOperationChecker checker) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NotNull InterceptorRegistry registry) {
                registry.addInterceptor(authentication)
                        .order(Ordered.HIGHEST_PRECEDENCE + 50);
                registry.addInterceptor(new OpenApiRequestInterceptor(checker))
                        .order(Ordered.HIGHEST_PRECEDENCE + 200);
            }
        };
    }
}
