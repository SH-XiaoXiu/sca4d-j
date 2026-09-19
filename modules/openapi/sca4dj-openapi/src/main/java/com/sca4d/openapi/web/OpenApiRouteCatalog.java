package com.sca4d.openapi.web;

import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.openapi.web.annotation.OpenApiRoute;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 扫描、校验并保存具体应用通过 Spring MVC 声明的 OpenAPI 路由。 */
public final class OpenApiRouteCatalog implements SmartInitializingSingleton {

    private final @NotNull ApplicationContext context;

    private final @NotNull OperationCatalog operations;

    private @NotNull List<@NotNull OpenApiRouteDefinition> routes = List.of();

    private boolean initialized;

    /**
     * 保存 Spring Controller 和 Operation 目录，等所有单例创建完成后扫描路由。
     *
     * @param context 应用 Spring 容器
     * @param operations 已安装且完成结构校验的 Operation 目录
     */
    public OpenApiRouteCatalog(
            @NotNull ApplicationContext context,
            @NotNull OperationCatalog operations) {
        this.context = Objects.requireNonNull(context, "Spring 容器不能为空");
        this.operations = Objects.requireNonNull(operations, "Operation 目录不能为空");
    }

    /** 在 Spring 完成全部单例创建后扫描并校验 OpenAPI 路由。 */
    @Override
    public synchronized void afterSingletonsInstantiated() {
        initialize();
    }

    /** 扫描并校验具体应用声明的路由；重复调用不会重新扫描或覆盖路由目录。 */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        Map<String, OpenApiRouteDefinition> byMapping = new LinkedHashMap<>();
        Map<String, OpenApiRouteDefinition> byTool = new LinkedHashMap<>();
        Map<String, OpenApiRouteDefinition> byOpenApiOperationId = new LinkedHashMap<>();
        List<OpenApiRouteDefinition> discovered = new ArrayList<>();
        for (Map.Entry<String, Object> entry : context.getBeansWithAnnotation(RestController.class).entrySet()) {
            Object handler = entry.getValue();
            Class<?> targetType = AopUtils.getTargetClass(handler);
            Map<Method, OpenApiRoute> methods = MethodIntrospector.selectMethods(
                    targetType,
                    (MethodIntrospector.MetadataLookup<OpenApiRoute>) method -> AnnotatedElementUtils
                            .findMergedAnnotation(method, OpenApiRoute.class));
            for (Method method : methods.keySet()) {
                OpenApiRouteDefinition route = new OpenApiRouteDefinition(
                        Objects.requireNonNull(methods.get(method), "OpenAPI 路由声明不能为空"));
                Operation operation = operations.actionByCode(route.operation()).operation();
                String mappingKey = route.method().name() + " " + route.path();
                if (byMapping.putIfAbsent(mappingKey, route) != null) {
                    throw new IllegalStateException("OpenAPI 路由重复: " + mappingKey);
                }
                String toolKey = route.toolGroup() + "." + route.toolName();
                if (byTool.putIfAbsent(toolKey, route) != null) {
                    throw new IllegalStateException("OpenAPI Tool 名称重复: " + toolKey);
                }
                if (byOpenApiOperationId.putIfAbsent(route.openApiOperationId(), route) != null) {
                    throw new IllegalStateException("OpenAPI 标准操作标识重复: " + route.openApiOperationId());
                }
                validatePermissionAnnotation(method, operation);
                discovered.add(route);
            }
        }
        discovered.sort(
                Comparator.comparing(OpenApiRouteDefinition::path)
                        .thenComparing(route -> route.method().name()));
        routes = List.copyOf(discovered);
        initialized = true;
    }

    /** @return 按路径和 HTTP 方法稳定排序的公开路由 */
    public @NotNull List<@NotNull OpenApiRouteDefinition> routes() {
        return routes;
    }

    /** 校验 Controller 方法声明了与开放 Operation 一致的 {@code @RequirePermission}。 */
    private static void validatePermissionAnnotation(Method method, Operation operation) {
        com.sca4d.core.api.annotation.RequirePermission permission = AnnotatedElementUtils
                .findMergedAnnotation(method, com.sca4d.core.api.annotation.RequirePermission.class);
        if (permission == null
                || !operation.value().equals(permission.operation())
                || permission.operation().isBlank()) {
            throw new IllegalStateException(
                    "OpenAPI 路由必须声明相同 Operation 的 @RequirePermission: " + method.toGenericString());
        }
    }
}
