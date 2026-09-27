package com.sca4d.core.spring.web;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.annotation.ETag;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 从 HTTP 响应数据中读取显式声明的强 ETag 版本。 */
class ResponseETagResolver {

    private final Map<Class<?>, Optional<Method>> methods = new ConcurrentHashMap<>();

    /** 创建响应 ETag 解析器。 */
    ResponseETagResolver() {
    }

    /**
     * 读取响应数据声明的版本。
     *
     * @param data 响应中的顶层 data；为空时不发布 ETag
     * @return 数据没有声明 {@link ETag} 或当前实例不是稳定表示时为空，否则返回声明方法产生的版本
     */
    @CheckReturnValue
    Optional<Version> resolve(@Nullable Object data) {
        if (data == null) {
            return Optional.empty();
        }
        Optional<Method> method = methods.computeIfAbsent(data.getClass(), ResponseETagResolver::findMethod);
        if (method.isEmpty()) {
            return Optional.empty();
        }
        try {
            Object value = method.get().invoke(data);
            if (value == null) {
                return Optional.empty();
            }
            if (!(value instanceof Version version)) {
                throw new IllegalStateException("@ETag 方法返回了错误类型: " + method.get());
            }
            return Optional.of(version);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("无法访问 @ETag 方法: " + method.get(), exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("@ETag 方法执行失败: " + method.get(), exception.getCause());
        }
    }

    /**
     * 校验响应数据类型中的 ETag 声明。
     *
     * @param type Controller 返回的顶层响应数据类型
     */
    void validate(@NotNull Class<?> type) {
        methods.computeIfAbsent(Objects.requireNonNull(type, "响应数据类型不能为空"), ResponseETagResolver::findMethod);
    }

    /** @return 类型中唯一合法的 {@link ETag} 方法；没有声明时为空 */
    private static @NotNull Optional<Method> findMethod(@NotNull Class<?> type) {
        Set<Method> tagged = new LinkedHashSet<>();
        Arrays.stream(ReflectionUtils.getAllDeclaredMethods(Objects.requireNonNull(type, "响应数据类型不能为空")))
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .filter(method -> method.isAnnotationPresent(ETag.class))
                .forEach(tagged::add);
        Arrays.stream(type.getMethods())
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .filter(method -> method.isAnnotationPresent(ETag.class))
                .forEach(tagged::add);
        if (tagged.isEmpty()) {
            return Optional.empty();
        }
        if (tagged.size() != 1) {
            throw new IllegalStateException("响应数据只能声明一个 @ETag 方法: " + type.getName());
        }
        Method method = tagged.iterator().next();
        if (!Modifier.isPublic(method.getModifiers())
                || method.getParameterCount() != 0
                || !Version.class.equals(method.getReturnType())) {
            throw new IllegalStateException("@ETag 方法必须公开、无参数并返回 Version: " + method);
        }
        return Optional.of(method);
    }
}
