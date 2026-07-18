package com.sca4d.common.web;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.stream.Collectors;

/**
 * 在 Spring MVC 序列化响应体前写入前端本地状态刷新信号。
 *
 * <p>Servlet 过滤器返回时响应体可能已经提交，无法再追加响应头。该实现运行在消息转换器写出响应体之前，保证成功和异常响应都能携带当前请求已登记的刷新信号。
 */
@ControllerAdvice
public class ClientStateResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 对所有由 Spring MVC 消息转换器写出的响应生效。
     *
     * @param returnType Controller 返回类型
     * @param converterType 消息转换器类型
     * @return 始终返回 true
     */
    @Override
    public boolean supports(
            @NotNull MethodParameter returnType,
            @NotNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * 在响应体写出前复制当前请求登记的状态刷新信号。
     *
     * @param body 原始响应体
     * @param returnType Controller 返回类型
     * @param selectedContentType 已选择的响应媒体类型
     * @param selectedConverterType 已选择的消息转换器类型
     * @param request 当前服务端请求
     * @param response 当前服务端响应
     * @return 原始响应体，不改变序列化内容
     */
    @Override
    public @Nullable Object beforeBodyWrite(
            @Nullable Object body,
            @NotNull MethodParameter returnType,
            @NotNull MediaType selectedContentType,
            @NotNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
            @NotNull ServerHttpRequest request,
            @NotNull ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> apiResponse) {
            HttpStatus responseStatus = apiResponse.responseStatus();
            if (responseStatus != null) {
                response.setStatusCode(responseStatus);
            }
            response.getHeaders().putAll(apiResponse.responseHeaders());
        }
        ClientState.Change change = ClientState.currentOrNull();
        if (change == null || !change.hasChange()) {
            return body;
        }
        if (change.isStale()) {
            response.getHeaders().set(ClientState.STALE_HEADER, "true");
        }
        if (!change.getRefreshTargets().isEmpty()) {
            response.getHeaders().set(
                    ClientState.REFRESH_HEADER,
                    change.getRefreshTargets().stream()
                            .map(ClientStateTarget::getValue)
                            .collect(Collectors.joining(",")));
        }
        if (change.getVersion() != null && !change.getVersion().isBlank()) {
            response.getHeaders().set(ClientState.VERSION_HEADER, change.getVersion());
        }
        return body;
    }
}
