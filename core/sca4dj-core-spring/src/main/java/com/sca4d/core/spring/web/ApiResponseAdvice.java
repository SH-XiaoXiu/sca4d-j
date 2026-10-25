package com.sca4d.core.spring.web;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.ETag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 在 Spring MVC 序列化 `ApiResponse` 前应用它声明的 HTTP 状态和响应头；成功响应的 data 声明了 {@code @ETag} 时写入强 ETag。 */
@ControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ResponseETagResolver etags = new ResponseETagResolver();

    public ApiResponseAdvice() {
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
        if (body instanceof ApiResponse<?> apiResponse) {
            HttpStatus status = apiResponse.responseStatus();
            if (status != null) {
                response.setStatusCode(status);
            }
            response.getHeaders().putAll(apiResponse.responseHeaders());
            if (apiResponse.success()) {
                etags.resolve(apiResponse.getData())
                        .ifPresent(
                                version -> response.getHeaders()
                                        .setETag(ETag.create(version.toString()).formattedTag()));
                if (response.getHeaders().getFirst(HttpHeaders.ETAG) != null) {
                    apiResponse.omitDynamicMetadata();
                }
            }
        }
        return body;
    }
}
