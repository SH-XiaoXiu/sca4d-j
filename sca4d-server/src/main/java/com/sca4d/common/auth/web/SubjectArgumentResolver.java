package com.sca4d.common.auth.web;

import com.sca4d.common.auth.MissingAuthException;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link Subject} 参数解析器。
 */
public class SubjectArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NotNull MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(AuthSubject.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        return Subject.class.isAssignableFrom(type) || Long.class.isAssignableFrom(type);
    }

    @Override
    public @Nullable Object resolveArgument(
            @NotNull MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            @NotNull NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
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
