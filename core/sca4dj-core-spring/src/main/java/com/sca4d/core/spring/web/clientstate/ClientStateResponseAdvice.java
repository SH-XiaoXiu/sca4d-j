package com.sca4d.core.spring.web.clientstate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 在 Spring MVC 序列化响应体前只写入当前请求产生的 ClientState 响应头。 */
@ControllerAdvice
public class ClientStateResponseAdvice implements ResponseBodyAdvice<Object> {

    public ClientStateResponseAdvice() {
    }

    /** {@inheritDoc} */
    @Override
    public boolean supports(
            @NotNull MethodParameter returnType,
            @NotNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * 把当前请求登记的状态刷新目标写入即将输出的响应头，响应体原样返回。
     *
     * @param body 即将序列化的响应体
     * @param returnType Controller 返回值声明
     * @param selectedContentType 已选中的响应媒体类型
     * @param selectedConverterType 已选中的消息转换器类型
     * @param request 当前请求
     * @param response 尚未提交的响应
     * @return 原响应体
     */
    @Override
    public @Nullable Object beforeBodyWrite(
            @Nullable Object body,
            @NotNull MethodParameter returnType,
            @NotNull MediaType selectedContentType,
            @NotNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
            @NotNull ServerHttpRequest request,
            @NotNull ServerHttpResponse response) {
        ClientStateHeaders.write(response.getHeaders());
        return body;
    }
}
