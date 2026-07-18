package com.sca4d.common.auth.web;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.MissingAuthException;
import com.sca4d.common.auth.annotation.AuthAccount;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@link Account} 参数解析器。 */
public class AccountArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NotNull MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(AuthAccount.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        return Account.class.isAssignableFrom(type) || Long.class.isAssignableFrom(type);
    }

    @Override
    public @Nullable Object resolveArgument(
            @NotNull MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            @NotNull NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        AuthAccount annotation = parameter.getParameterAnnotation(AuthAccount.class);
        Account account = CurrentRequestHolder.current().getAccount();
        if (account == null && !(annotation != null && annotation.nullable())) {
            throw new MissingAuthException();
        }
        if (Account.class.isAssignableFrom(parameter.getParameterType())) {
            return account;
        }
        return account == null ? null : account.getAccountId();
    }
}
