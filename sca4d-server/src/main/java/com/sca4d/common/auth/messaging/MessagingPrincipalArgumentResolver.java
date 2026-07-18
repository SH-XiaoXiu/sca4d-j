package com.sca4d.common.auth.messaging;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalInjection;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;

/**
 * Spring Messaging {@link Principal} 参数解析器。
 */
public class MessagingPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@Nullable MethodParameter parameter) {
        if (parameter == null || !parameter.hasParameterAnnotation(AuthPrincipal.class)) {
            return false;
        }
        return PrincipalInjection.supports(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(@Nullable MethodParameter parameter, @Nullable Message<?> message) {
        if (parameter == null) {
            return null;
        }
        AuthPrincipal annotation = parameter.getParameterAnnotation(AuthPrincipal.class);
        if (annotation == null) {
            return null;
        }
        Principal principal = CurrentRequestHolder.current().getPrincipal();
        if (principal == null) {
            // payload 参与选择时，AccessAspect 会在业务方法调用前替换为最终 Principal、ID 或类型。
            return null;
        }
        return PrincipalInjection.value(
                parameter.getParameterType(), annotation, principal);
    }
}
