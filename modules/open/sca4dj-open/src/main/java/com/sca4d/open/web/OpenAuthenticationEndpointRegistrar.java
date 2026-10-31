package com.sca4d.open.web;

import com.sca4d.open.exception.OpenException;
import com.sca4d.open.oauth.OpenAuthenticationEndpoint;
import com.sca4d.open.oauth.OpenTokenEndpointHandler;
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

/** 按具体应用配置注册平台开放 Client Credentials 认证端点。 */
public final class OpenAuthenticationEndpointRegistrar implements InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAuthenticationEndpointRegistrar.class);

    private final @NotNull OpenAuthenticationEndpoint endpoint;

    private final @NotNull OpenTokenEndpointHandler handler;

    private final @NotNull RequestMappingHandlerMapping mappings;

    /** 创建平台开放认证端点注册器。 */
    public OpenAuthenticationEndpointRegistrar(
            @NotNull RequestMappingHandlerMapping mappings,
            @NotNull OpenAuthenticationEndpoint endpoint,
            @NotNull OpenTokenEndpointHandler handler) {
        this.mappings = Objects.requireNonNull(mappings, "Spring MVC 路由映射不能为空");
        this.endpoint = Objects.requireNonNull(endpoint, "Open 认证端点不能为空");
        this.handler = Objects.requireNonNull(handler, "Open Token Handler 不能为空");
    }

    /** 注册 POST 认证路径，路径冲突时阻止应用启动。 */
    @Override
    public void afterPropertiesSet() {
        RequestMappingInfo mapping = RequestMappingInfo.paths(endpoint.path())
                .methods(RequestMethod.POST)
                .consumes(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .build();
        mappings.registerMapping(mapping, handler, tokenMethod());
        LOG.info("Open 认证端点已注册 | method=POST path={} flow=client_credentials", endpoint.path());
    }

    /** 读取动态路由使用的公开 Token 方法。 */
    private static @NotNull Method tokenMethod() {
        try {
            return OpenTokenEndpointHandler.class.getMethod("token", String.class, String.class);
        } catch (NoSuchMethodException exception) {
            throw new OpenException("Open Token Handler 方法不存在", exception);
        }
    }
}
