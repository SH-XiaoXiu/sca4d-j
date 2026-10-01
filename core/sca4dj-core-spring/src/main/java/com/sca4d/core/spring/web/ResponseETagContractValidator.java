package com.sca4d.core.spring.web;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/** 在应用启动时校验 Controller 返回的顶层响应数据 ETag 声明。 */
public class ResponseETagContractValidator implements SmartInitializingSingleton {

    private final RequestMappingHandlerMapping mappings;

    private final ResponseETagResolver etags = new ResponseETagResolver();

    /**
     * 创建 Controller 响应 ETag 契约校验器。
     *
     * @param mappings 应用实际注册的 Spring MVC 路由
     */
    public ResponseETagContractValidator(@NotNull RequestMappingHandlerMapping mappings) {
        this.mappings = Objects.requireNonNull(mappings, "Spring MVC 路由不能为空");
    }

    /** 校验所有顶层 {@code ApiResponse.data} 具体类型，声明错误时阻止应用启动。 */
    @Override
    public void afterSingletonsInstantiated() {
        mappings.getHandlerMethods()
                .values()
                .stream()
                .map(HandlerMethod::getMethod)
                .map(ResponseETagContractValidator::responseDataType)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(etags::validate);
    }

    /** @return Controller 返回的顶层 {@code ApiResponse.data} 具体类型；其他返回形式为空 */
    private static @Nullable Class<?> responseDataType(@NotNull Method method) {
        Type returnType = Objects.requireNonNull(method, "Controller 方法不能为空").getGenericReturnType();
        if (!(returnType instanceof ParameterizedType responseType)
                || !ApiResponse.class.equals(responseType.getRawType())) {
            return null;
        }
        Type dataType = responseType.getActualTypeArguments()[0];
        return dataType instanceof Class<?> dataClass ? dataClass : null;
    }
}
