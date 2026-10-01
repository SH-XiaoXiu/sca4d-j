package com.sca4d.core.spring.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 从当前 HTTP 请求和已经绑定的 Controller 参数收集业务身份选择输入。 */
public class PrincipalSelectionInputResolver {

    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    public PrincipalSelectionInputResolver() {
    }

    /**
     * 收集当前业务身份选择器可使用的 HTTP 输入。
     *
     * <p>固定名称 {@code path}、{@code query}、{@code header} 和 {@code body} 分别表示路径变量、查询参数、
     * 请求头和请求体。可发现的 Controller 方法参数也按参数名写入同一输入。
     *
     * @param method 当前 Controller 方法
     * @param arguments Spring 已经绑定的方法参数
     * @param request 当前 HTTP 请求
     * @return 保持参数名和 HTTP 输入结构的选择输入
     */
    public @NotNull PrincipalSelectionInput resolve(
            @NotNull Method method,
            Object @NotNull [] arguments,
            @NotNull HttpServletRequest request) {
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        String[] names = parameterNames.getParameterNames(method);
        if (names != null) {
            for (int index = 0; index < names.length; index++) {
                values.put(names[index], arguments[index]);
            }
        }
        values.put("path", pathVariables(request));
        values.put("query", queryParameters(request));
        values.put("header", headers(request));
        values.put("body", requestBody(method, arguments));
        return new PrincipalSelectionInput(values);
    }

    /**
     * 在 MVC 参数绑定前收集业务身份选择输入。
     *
     * <p>path、query 和 header 使用当前请求值；request body 与 Controller 方法参数使用空值。只有
     * {@link PrincipalSelection#supportsSelectionBeforeArgumentBinding()} 返回 {@code true} 的选择器可以使用本结果。
     *
     * @param method 当前 Controller 方法
     * @param request 当前 HTTP 请求
     * @return 不读取请求体的选择输入
     */
    public @NotNull PrincipalSelectionInput resolveBeforeArgumentBinding(
            @NotNull Method method,
            @NotNull HttpServletRequest request) {
        return resolve(method, new Object[method.getParameterCount()], request);
    }

    /**
     * 读取 Spring MVC 已匹配的路径变量。
     *
     * @param request 当前 HTTP 请求
     * @return 不可修改的路径变量；请求尚无路径变量时为空 Map
     */
    private @NotNull Map<String, Object> pathVariables(@NotNull HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables)) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        variables.forEach((name, value) -> values.put(String.valueOf(name), value));
        return Collections.unmodifiableMap(values);
    }

    /**
     * 读取查询参数，单值保留为字符串，多值保留为不可修改列表。
     *
     * @param request 当前 HTTP 请求
     * @return 不可修改的查询参数
     */
    private @NotNull Map<String, Object> queryParameters(@NotNull HttpServletRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, entries) -> values.put(name, scalarOrList(entries)));
        return Collections.unmodifiableMap(values);
    }

    /**
     * 读取名称已转为小写的请求头，单值保留为字符串，多值保留为不可修改列表。
     *
     * @param request 当前 HTTP 请求
     * @return 不可修改的请求头；容器不提供请求头枚举时为空 Map
     */
    private @NotNull Map<String, Object> headers(@NotNull HttpServletRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return Collections.unmodifiableMap(values);
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            List<String> entries = Collections.list(request.getHeaders(name));
            values.put(
                    name.toLowerCase(Locale.ROOT),
                    entries.size() == 1 ? entries.getFirst() : List.copyOf(entries));
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * 查找 Controller 已绑定的第一个请求体参数。
     *
     * @param method 当前 Controller 方法
     * @param arguments Spring 已绑定的方法参数
     * @return 请求体对象；方法没有请求体参数或参数尚未绑定时为空
     */
    private @Nullable Object requestBody(
            @NotNull Method method,
            Object @NotNull [] arguments) {
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index].isAnnotationPresent(RequestBody.class)) {
                return arguments[index];
            }
        }
        return null;
    }

    /**
     * 保留单个协议值，多值复制为不可修改列表。
     *
     * @param values 同名查询参数或请求头的全部值
     * @return 单个字符串或不可修改字符串列表
     */
    private @NotNull Object scalarOrList(String @NotNull [] values) {
        return values.length == 1 ? values[0] : List.copyOf(Arrays.asList(values));
    }
}
