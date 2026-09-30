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

    /** {@inheritDoc} */
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
