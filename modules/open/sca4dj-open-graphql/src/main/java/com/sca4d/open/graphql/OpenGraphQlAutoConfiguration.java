package com.sca4d.open.graphql;

import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.open.auth.OpenAuthenticationService;
import com.sca4d.open.client.OpenClientOperationChecker;
import com.sca4d.open.graphql.config.OpenGraphQlProperties;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.server.WebGraphQlInterceptor;

/** 自动装配平台开放 GraphQL 的 Schema 指令和 Bearer 认证。 */
@AutoConfiguration
@ConditionalOnClass(WebGraphQlInterceptor.class)
@ConditionalOnProperty(prefix = "sca4dj.open.graphql", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OpenGraphQlProperties.class)
public class OpenGraphQlAutoConfiguration {

    /** 注册开放 Operation Schema 指令。 */
    @Bean
    RuntimeWiringConfigurer openGraphQlRuntimeWiringConfigurer(
            OperationCatalog operations,
            OpenClientOperationChecker checker) {
        return new OpenGraphQlRuntimeWiringConfigurer(operations, checker);
    }

    /** 注册 GraphQL Bearer 认证拦截器。 */
    @Bean
    WebGraphQlInterceptor openGraphQlWebInterceptor(
            OpenAuthenticationService authentication,
            OpenGraphQlProperties properties) {
        return new OpenGraphQlWebInterceptor(authentication, properties);
    }

    /** 注册 GraphQL 业务错误转换器。 */
    @Bean
    DataFetcherExceptionResolver openGraphQlExceptionResolver() {
        return new OpenGraphQlExceptionResolver();
    }

    /** 限制 GraphQL 查询的最大嵌套深度。 */
    @Bean
    Instrumentation openGraphQlDepthInstrumentation(OpenGraphQlProperties properties) {
        return new MaxQueryDepthInstrumentation(properties.getMaxDepth());
    }

    /** 限制 GraphQL 查询的最大复杂度。 */
    @Bean
    Instrumentation openGraphQlComplexityInstrumentation(OpenGraphQlProperties properties) {
        return new MaxQueryComplexityInstrumentation(properties.getMaxComplexity());
    }
}
