package com.sca4d.common.auth.web;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalInjection;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link Principal} 参数解析器。
 */
public class PrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NotNull MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(AuthPrincipal.class)) {
            return false;
        }
        return PrincipalInjection.supports(parameter.getParameterType());
    }

    @Override
    public @Nullable Object resolveArgument(
            @NotNull MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            @NotNull NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        AuthPrincipal annotation = parameter.getParameterAnnotation(AuthPrincipal.class);
        if (annotation == null) {
            return null;
        }
        Principal principal = CurrentRequestHolder.current().getPrincipal();
        if (principal == null) {
            // body 参与选择时，AccessAspect 会在业务方法调用前替换为最终 Principal、ID 或类型。
            return null;
        }
        return PrincipalInjection.value(
                parameter.getParameterType(), annotation, principal);
    }

}
