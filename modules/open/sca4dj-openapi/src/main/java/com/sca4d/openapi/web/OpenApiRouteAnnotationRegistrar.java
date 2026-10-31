package com.sca4d.openapi.web;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.openapi.web.annotation.OpenApiRoute;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/**
 * 把 {@link OpenApiRoute} 这种便捷声明转换为启动期路由注册。
 *
 * <p>此类只在应用启动时读取注解。运行时认证、授权和文档生成只读取 `OpenApiRouteCatalog`。
 */
public final class OpenApiRouteAnnotationRegistrar implements OpenApiRouteRegistrar {

    private final @NotNull ApplicationContext context;

    /** 保存用于扫描 Controller 的 Spring 容器。 */
    public OpenApiRouteAnnotationRegistrar(@NotNull ApplicationContext context) {
        this.context = Objects.requireNonNull(context, "Spring 容器不能为空");
    }

    /** 扫描带 `@OpenApiRoute` 的 Controller 方法，并转换为路由定义。 */
    @Override
    public void register(@NotNull OpenApiRouteCatalog routes) {
        for (Object controller : context.getBeansWithAnnotation(RestController.class).values()) {
            Class<?> targetType = AopUtils.getTargetClass(controller);
            Map<Method, OpenApiRoute> methods = MethodIntrospector.selectMethods(
                    targetType,
                    (MethodIntrospector.MetadataLookup<OpenApiRoute>) method -> AnnotatedElementUtils
                            .findMergedAnnotation(method, OpenApiRoute.class));
            methods.forEach((method, route) -> routes.register(method, definition(route)));
        }
    }

    /** 校验注解中的单一 HTTP 方法和路径，并创建路由定义。 */
    private static @NotNull OpenApiRouteDefinition definition(@NotNull OpenApiRoute annotation) {
        Objects.requireNonNull(annotation, "OpenAPI 路由声明不能为空");
        if (annotation.method().length != 1 || annotation.method()[0] == null) {
            throw new IllegalArgumentException("OpenAPI 路由必须声明一个 HTTP 方法");
        }
        if (annotation.path().length != 1) {
            throw new IllegalArgumentException("OpenAPI 路由必须声明一个路径");
        }
        return new OpenApiRouteDefinition(
                annotation.method()[0],
                annotation.path()[0],
                annotation.toolGroup(),
                annotation.toolName(),
                TreeCode.ofColon(annotation.operation()));
    }
}
