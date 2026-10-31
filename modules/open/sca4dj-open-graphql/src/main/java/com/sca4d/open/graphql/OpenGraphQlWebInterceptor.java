package com.sca4d.open.graphql;

import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.open.auth.OpenAuthenticationService;
import com.sca4d.open.graphql.config.OpenGraphQlProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.util.Objects;

/** 在 GraphQL 执行前恢复开放平台 Bearer Token。 */
public class OpenGraphQlWebInterceptor implements WebGraphQlInterceptor {

    private final @NotNull OpenAuthenticationService authentication;

    private final @NotNull OpenGraphQlProperties properties;

    /** 创建 GraphQL 开放认证拦截器。 */
    public OpenGraphQlWebInterceptor(
            @NotNull OpenAuthenticationService authentication,
            @NotNull OpenGraphQlProperties properties) {
        this.authentication = Objects.requireNonNull(authentication, "Open 认证服务不能为空");
        this.properties = Objects.requireNonNull(properties, "GraphQL 配置不能为空");
    }

    /** 将 HTTP Authorization 头转换为当前 Call 的 Session。 */
    @Override
    public @NotNull Mono<WebGraphQlResponse> intercept(
            @NotNull WebGraphQlRequest request,
            @NotNull Chain chain) {
        if (CallHolder.currentOrNull() != null) {
            if (!properties.isIntrospectionEnabled()
                    && (request.getDocument().contains("__schema") || request.getDocument().contains("__type"))) {
                return Mono.error(new BusinessException(CoreErrorCode.PERMISSION_DENIED));
            }
            authentication.restoreAuthorization(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                    .ifPresent(CallHolder.current()::authenticate);
        }
        return chain.next(request);
    }
}
