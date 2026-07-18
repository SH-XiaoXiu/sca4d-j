package com.sca4d.common.auth.messaging;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.MissingAuthException;
import com.sca4d.common.auth.annotation.AuthAccount;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;

/** Spring Messaging {@link Account} 参数解析器。 */
public class MessagingAccountArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@Nullable MethodParameter parameter) {
        if (parameter == null || !parameter.hasParameterAnnotation(AuthAccount.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        return Account.class.isAssignableFrom(type) || Long.class.isAssignableFrom(type);
    }

    @Override
    public Object resolveArgument(@Nullable MethodParameter parameter, @Nullable Message<?> message) {
        if (parameter == null) {
            return null;
        }
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
