package com.sca4d.openapi.web;

import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationCatalog;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 保存启动期校验完成的 OpenAPI 路由元数据，为 HTTP、Call 和 Springdoc 提供同一份查询快照。
 */
public class OpenApiRouteCatalog implements SmartInitializingSingleton {

    private final @NotNull List<@NotNull OpenApiRouteRegistrar> registrars;

    private final @NotNull OperationCatalog operations;

    private final @NotNull RequestMappingHandlerMapping mappings;

    private final Map<MethodKey, OpenApiRouteDefinition> pending = new LinkedHashMap<>();

    private volatile @NotNull Snapshot snapshot = Snapshot.empty();

    private boolean initialized;

    /**
     * 保存 Spring MVC 映射、Operation 目录和路由注册器；所有注册器完成后冻结路由快照。
     *
     * @param mappings Spring MVC 已注册的 Handler 映射
     * @param operations 已安装且完成结构校验的 Operation 目录
     * @param registrars 注解适配器和具体应用提供的注册器
     */
    public OpenApiRouteCatalog(
            @NotNull RequestMappingHandlerMapping mappings,
            @NotNull OperationCatalog operations,
            @NotNull ObjectProvider<OpenApiRouteRegistrar> registrars) {
        this.mappings = Objects.requireNonNull(mappings, "Spring MVC 映射不能为空");
        this.operations = Objects.requireNonNull(operations, "Operation 目录不能为空");
        this.registrars = Objects.requireNonNull(registrars, "OpenAPI 路由注册器不能为空")
                .orderedStream()
                .toList();
    }

    /** 创建供测试或非 Spring 装配使用的路由目录。 */
    public OpenApiRouteCatalog(
            @NotNull RequestMappingHandlerMapping mappings,
            @NotNull OperationCatalog operations,
            @NotNull List<? extends OpenApiRouteRegistrar> registrars) {
        this.mappings = Objects.requireNonNull(mappings, "Spring MVC 映射不能为空");
        this.operations = Objects.requireNonNull(operations, "Operation 目录不能为空");
        this.registrars = List.copyOf(Objects.requireNonNull(registrars, "OpenAPI 路由注册器不能为空"));
    }

    /** 在 Spring 完成全部单例创建后收集并校验路由。 */
    @Override
    public synchronized void afterSingletonsInstantiated() {
        initialize();
    }

    /** 收集、校验并冻结路由目录；重复调用不会改变已发布的快照。 */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        for (OpenApiRouteRegistrar registrar : registrars) {
            registrar.register(this);
        }
        Map<MethodKey, HandlerMethod> handlers = handlerMethods();
        Map<String, OpenApiRouteDefinition> byMapping = new LinkedHashMap<>();
        Map<String, OpenApiRouteDefinition> byTool = new LinkedHashMap<>();
        Map<String, OpenApiRouteDefinition> byOperationId = new LinkedHashMap<>();
        List<OpenApiRouteDefinition> routes = new ArrayList<>();
        for (Map.Entry<MethodKey, OpenApiRouteDefinition> entry : pending.entrySet()) {
            if (!handlers.containsKey(entry.getKey())) {
                throw new IllegalStateException(
                        "OpenAPI 路由没有对应的 Spring MVC Handler: " + entry.getKey());
            }
            OpenApiRouteDefinition route = entry.getValue();
            String mappingKey = route.method().name() + " " + route.path();
            if (byMapping.putIfAbsent(mappingKey, route) != null) {
                throw new IllegalStateException("OpenAPI 路由重复: " + mappingKey);
            }
            String toolKey = route.toolGroup() + "." + route.toolName();
            if (byTool.putIfAbsent(toolKey, route) != null) {
                throw new IllegalStateException("OpenAPI Tool 名称重复: " + toolKey);
            }
            String operationId = route.openApiOperationId();
            if (byOperationId.putIfAbsent(operationId, route) != null) {
                throw new IllegalStateException("OpenAPI 标准操作标识重复: " + operationId);
            }
            routes.add(route);
        }
        routes.sort(
                Comparator.comparing(OpenApiRouteDefinition::path)
                        .thenComparing(route -> route.method().name()));
        snapshot = Snapshot.of(pending, routes);
        initialized = true;
    }

    /**
     * 登记一个已经由 Spring MVC 映射的 Controller 方法。
     *
     * @param controllerType Controller 的具体类型
     * @param methodName 方法名称
     * @param definition OpenAPI 路由定义
     * @param parameterTypes 方法参数类型；方法重载时必须明确填写
     */
    public synchronized void register(
            @NotNull Class<?> controllerType,
            @NotNull String methodName,
            @NotNull OpenApiRouteDefinition definition,
            Class<?>... parameterTypes) {
        Method method;
        try {
            method = controllerType.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException exception) {
            try {
                method = controllerType.getMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException missing) {
                throw new IllegalArgumentException(
                        "OpenAPI Controller 方法不存在: " + controllerType.getName() + "#" + methodName,
                        missing);
            }
        }
        register(method, definition);
    }

    /** 登记一个已经由 Spring MVC 映射的具体方法。 */
    public synchronized void register(
            @NotNull Method method,
            @NotNull OpenApiRouteDefinition definition) {
        if (initialized) {
            throw new IllegalStateException("OpenAPI 路由目录已经冻结");
        }
        Method concrete = concreteMethod(Objects.requireNonNull(method, "Controller 方法不能为空"));
        OpenApiRouteDefinition checked = Objects.requireNonNull(definition, "OpenAPI 路由定义不能为空");
        Operation operation = operations.actionByCode(checked.operation()).operation();
        validatePermissionAnnotation(concrete, operation);
        MethodKey key = MethodKey.of(concrete);
        if (pending.putIfAbsent(key, checked) != null) {
            throw new IllegalStateException("OpenAPI Controller 方法重复注册: " + key);
        }
    }

    /** @return 按路径和 HTTP 方法稳定排序的公开路由 */
    public @NotNull List<@NotNull OpenApiRouteDefinition> routes() {
        return snapshot.routes();
    }

    /** 根据 Spring MVC HandlerMethod 查询开放路由。 */
    public @NotNull Optional<OpenApiRouteDefinition> find(@NotNull HandlerMethod handler) {
        MethodKey key = MethodKey.of(handler.getBeanType(), handler.getMethod());
        return Optional.ofNullable(snapshot.byMethod().get(key));
    }

    /** 根据 CallExecution 的目标类型和方法查询开放路由。 */
    public @NotNull Optional<OpenApiRouteDefinition> find(
            @NotNull Class<?> targetType,
            @NotNull Method method) {
        MethodKey key = MethodKey.of(targetType, method);
        return Optional.ofNullable(snapshot.byMethod().get(key));
    }

    /** 判断方法是否属于已登记的开放路由，供 Springdoc 的方法过滤器使用。 */
    public boolean contains(@NotNull Method method) {
        return snapshot.byMethod().containsKey(MethodKey.of(method));
    }

    private Map<MethodKey, HandlerMethod> handlerMethods() {
        Map<MethodKey, HandlerMethod> result = new LinkedHashMap<>();
        for (HandlerMethod handler : mappings.getHandlerMethods().values()) {
            result.put(MethodKey.of(handler.getBeanType(), handler.getMethod()), handler);
        }
        return result;
    }

    private static @NotNull Method concreteMethod(@NotNull Method method) {
        return BridgeMethodResolver.findBridgedMethod(
                AopUtils.getMostSpecificMethod(method, method.getDeclaringClass()));
    }

    /** 校验开放 Operation 与业务权限声明一致，避免 Client 权限和 Controller 权限指向不同操作。 */
    private static void validatePermissionAnnotation(Method method, Operation operation) {
        RequirePermission permission = AnnotatedElementUtils
                .findMergedAnnotation(method, RequirePermission.class);
        if (permission == null
                || permission.operation().isBlank()
                || !operation.code().equals(new Operation(permission.operation()).code())) {
            throw new IllegalStateException(
                    "OpenAPI 路由必须声明相同 Operation 的 @RequirePermission: " + method.toGenericString());
        }
    }

    /** 保存已冻结的路由索引。 */
    private static final class Snapshot {
        private final @NotNull List<@NotNull OpenApiRouteDefinition> routes;

        private final @NotNull Map<@NotNull MethodKey, @NotNull OpenApiRouteDefinition> byMethod;

        private Snapshot(
                @NotNull List<@NotNull OpenApiRouteDefinition> routes,
                @NotNull Map<@NotNull MethodKey, @NotNull OpenApiRouteDefinition> byMethod) {
            this.routes = routes;
            this.byMethod = byMethod;
        }

        private static @NotNull Snapshot empty() {
            return new Snapshot(List.of(), Map.of());
        }

        private static @NotNull Snapshot of(
                @NotNull Map<MethodKey, OpenApiRouteDefinition> pending,
                @NotNull List<OpenApiRouteDefinition> routes) {
            return new Snapshot(List.copyOf(routes), Map.copyOf(pending));
        }

        private @NotNull List<OpenApiRouteDefinition> routes() {
            return routes;
        }

        private @NotNull Map<MethodKey, OpenApiRouteDefinition> byMethod() {
            return byMethod;
        }
    }

    /** 以具体 Controller 类型、方法名称和参数类型标识方法，避免代理方法与接口方法混淆。 */
    private static final class MethodKey {
        private final @NotNull Class<?> type;

        private final @NotNull List<@NotNull Class<?>> parameterTypes;

        private final @NotNull String name;

        private MethodKey(
                @NotNull Class<?> type,
                @NotNull String name,
                @NotNull List<Class<?>> parameterTypes) {
            this.type = type;
            this.name = name;
            this.parameterTypes = List.copyOf(parameterTypes);
        }

        private static @NotNull MethodKey of(@NotNull Method method) {
            return of(method.getDeclaringClass(), method);
        }

        private static @NotNull MethodKey of(@NotNull Class<?> type, @NotNull Method method) {
            Method concrete = BridgeMethodResolver.findBridgedMethod(
                    AopUtils.getMostSpecificMethod(method, type));
            return new MethodKey(
                    type,
                    concrete.getName(),
                    List.of(concrete.getParameterTypes()));
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof MethodKey key
                            && type.equals(key.type)
                            && name.equals(key.name)
                            && parameterTypes.equals(key.parameterTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name, parameterTypes);
        }

        @Override
        public String toString() {
            return type.getName() + "#" + name + parameterTypes;
        }
    }
}
