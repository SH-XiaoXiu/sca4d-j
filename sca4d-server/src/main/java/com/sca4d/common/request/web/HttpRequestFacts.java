package com.sca4d.common.request.web;

import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.HandlerMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 Servlet 请求提取授权表达式需要的路径变量、查询参数和请求头。
 *
 * <p>这里不解析业务入参，也不选择 Principal。path、query 和 header 会写入 {@code CurrentRequest}，供
 * Principal 选择与业务模块资源访问表达式在同一请求中复用。
 */
public final class HttpRequestFacts {

    /**
     * 禁止创建纯静态 HTTP 请求数据读取器。
     */
    private HttpRequestFacts() {
    }

    /**
     * 读取 Spring 路由匹配得到的路径变量。
     *
     * @param request 当前 Servlet 请求
     * @return 以变量名为键的路径变量；未命中模板路由时为空映射
     */
    @SuppressWarnings("unchecked")
    public static @NotNull Map<String, Object> pathVariables(@NotNull HttpServletRequest request) {
        Object value = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /**
     * 读取请求头的单值表示。
     *
     * @param request 当前 Servlet 请求
     * @return 以请求头名称为键的首个值；不存在请求头时为空映射
     */
    public static @NotNull Map<String, String> headers(@NotNull HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        var names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    /**
     * 读取查询参数，并保留单值与重复值的区别。
     *
     * <p>单值参数保存为 {@link String}，重复参数保存为不可变 {@link List}。授权表达式把重复参数用作
     * 数字或选择键时会明确拒绝，避免数组的 {@code toString()} 产生不可预测的授权输入。
     *
     * @param request 当前 Servlet 请求
     * @return 以参数名为键的单值字符串或不可变字符串列表
     */
    public static @NotNull Map<String, Object> queryParameters(
            @NotNull HttpServletRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (values.length == 1) {
                parameters.put(name, values[0]);
                return;
            }
            parameters.put(name, List.of(values));
        });
        return parameters;
    }
}
