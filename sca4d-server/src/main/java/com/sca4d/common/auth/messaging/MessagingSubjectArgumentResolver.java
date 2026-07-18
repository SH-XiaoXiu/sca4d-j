package com.sca4d.common.auth.messaging;

import com.sca4d.common.auth.MissingAuthException;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;

/**
 * Spring Messaging {@link Subject} 参数解析器。
 */
public class MessagingSubjectArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@Nullable MethodParameter parameter) {
        if (parameter == null || !parameter.hasParameterAnnotation(AuthSubject.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        return Subject.class.isAssignableFrom(type) || Long.class.isAssignableFrom(type);
    }

    @Override
    public Object resolveArgument(@Nullable MethodParameter parameter, @Nullable Message<?> message) {
        if (parameter == null) {
            return null;
        }
        AuthSubject annotation = parameter.getParameterAnnotation(AuthSubject.class);
        Subject subject = CurrentRequestHolder.current().getSubject();
        if (subject == null && !(annotation != null && annotation.nullable())) {
            throw new MissingAuthException();
        }
        if (Subject.class.isAssignableFrom(parameter.getParameterType())) {
            return subject;
        }
        return subject == null ? null : subject.getSubjectId();
    }
}
