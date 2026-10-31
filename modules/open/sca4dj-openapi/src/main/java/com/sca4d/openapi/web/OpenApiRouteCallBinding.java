package com.sca4d.openapi.web;

import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Objects;

/** 通过路由目录匹配开放 Controller 方法，使方法无需 OpenAPI 注解也能进入 Call 生命周期。 */
public final class OpenApiRouteCallBinding implements CallLifecycleAdvisor.MethodBinding {

    private final @NotNull OpenApiRouteCatalog routes;

    /** 保存启动期冻结的 OpenAPI 路由目录。 */
    public OpenApiRouteCallBinding(@NotNull OpenApiRouteCatalog routes) {
        this.routes = Objects.requireNonNull(routes, "OpenAPI 路由目录不能为空");
    }

    /** 判断具体方法是否已经登记为开放 API 路由。 */
    @Override
    public boolean matches(@NotNull Method method, @NotNull Class<?> targetType) {
        return routes.find(targetType, method).isPresent();
    }
}
