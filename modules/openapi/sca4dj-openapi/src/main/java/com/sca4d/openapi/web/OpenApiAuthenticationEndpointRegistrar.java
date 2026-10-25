package com.sca4d.openapi.web;

import com.sca4d.openapi.exception.OpenapiException;
import com.sca4d.openapi.oauth.OpenApiAuthenticationEndpoint;
import com.sca4d.openapi.oauth.OpenApiTokenEndpointHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.Objects;

/** 按具体应用提供的 Bean 注册模块实现的 OpenAPI Client Credentials 认证端点。 */
public final class OpenApiAuthenticationEndpointRegistrar implements InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(OpenApiAuthenticationEndpointRegistrar.class);

    private final @NotNull OpenApiAuthenticationEndpoint endpoint;

    private final @NotNull OpenApiTokenEndpointHandler handler;

    private final @NotNull RequestMappingHandlerMapping mappings;

    /**
     * 创建认证端点注册器。
     *
     * @param mappings Spring MVC 路由映射
     * @param endpoint 具体应用声明的认证端点
     * @param handler 模块实现的 Client Credentials Handler
     */
    public OpenApiAuthenticationEndpointRegistrar(
            @NotNull RequestMappingHandlerMapping mappings,
            @NotNull OpenApiAuthenticationEndpoint endpoint,
            @NotNull OpenApiTokenEndpointHandler handler) {
        this.mappings = Objects.requireNonNull(mappings, "Spring MVC 路由映射不能为空");
        this.endpoint = Objects.requireNonNull(endpoint, "OpenAPI 认证端点不能为空");
        this.handler = Objects.requireNonNull(handler, "OpenAPI Token Handler 不能为空");
    }

    /** 注册应用选择的 POST 路径，映射冲突时阻止应用启动。 */
    @Override
    public void afterPropertiesSet() {
        RequestMappingInfo mapping = RequestMappingInfo.paths(endpoint.path())
                .methods(RequestMethod.POST)
                .consumes(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .build();
        mappings.registerMapping(mapping, handler, tokenMethod());
        LOG.info(
                "OpenAPI 认证端点已注册 | method=POST path={} flow=client_credentials",
                endpoint.path());
    }

    /** 返回承接动态路由的公开 Handler 方法。 */
    private static @NotNull Method tokenMethod() {
        try {
            return OpenApiTokenEndpointHandler.class.getMethod("token", String.class, String.class);
        } catch (NoSuchMethodException exception) {
            throw new OpenapiException("OpenAPI Token Handler 方法不存在", exception);
        }
    }
}
