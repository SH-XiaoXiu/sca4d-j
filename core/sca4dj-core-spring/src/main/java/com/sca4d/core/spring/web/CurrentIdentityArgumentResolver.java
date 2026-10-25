package com.sca4d.core.spring.web;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.web.annotation.CurrentAccount;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.CurrentSession;
import com.sca4d.core.spring.web.annotation.CurrentSubject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 将当前请求已经确认的会话、主体、账号和业务身份注入 Controller 参数。 */
public class CurrentIdentityArgumentResolver implements HandlerMethodArgumentResolver {

    public CurrentIdentityArgumentResolver() {
    }

    /**
     * 判断参数是否声明了当前身份注解。
     *
     * @param parameter Controller 方法参数
     * @return 参数声明当前会话、主体、账号或业务身份注解时返回 {@code true}
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentSession.class)
                || parameter.hasParameterAnnotation(CurrentSubject.class)
                || parameter.hasParameterAnnotation(CurrentAccount.class)
                || parameter.hasParameterAnnotation(CurrentPrincipal.class);
    }

    /**
     * 注入当前请求已经确认的身份对象或对应标识。
     *
     * @param parameter Controller 方法参数
     * @param modelAndViewContainer 当前 MVC 的 `ModelAndViewContainer`
     * @param webRequest 当前 HTTP 请求
     * @param binderFactory 数据绑定工厂
     * @return 注解对应的身份对象、标识或允许的空值
     * @throws IllegalStateException 当前线程没有 Call，或参数类型与注解不匹配、nullable 的 primitive long 声明错误时抛出
     * @throws BusinessException 身份未确定且注解未允许空值时抛出 AUTHENTICATION_REQUIRED
     */
    @Override
    public @Nullable Object resolveArgument(
            MethodParameter parameter,
            @Nullable ModelAndViewContainer modelAndViewContainer,
            @NotNull NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        Call call = CallHolder.current();
        if (parameter.hasParameterAnnotation(CurrentSession.class)) {
            return session(parameter, call);
        }
        if (parameter.hasParameterAnnotation(CurrentSubject.class)) {
            return subject(parameter, call);
        }
        if (parameter.hasParameterAnnotation(CurrentAccount.class)) {
            return account(parameter, call);
        }
        return principal(parameter, call);
    }

    /**
     * 解析当前 Session 或 SessionId 参数。
     *
     * @param parameter Controller 方法参数
     * @param call 当前调用
     * @return 当前 Session、SessionId 或注解允许的空值
     */
    private @Nullable Object session(
            @NotNull MethodParameter parameter,
            @NotNull Call call) {
        Session session = call.session().orElse(null);
        if (session == null) {
            CurrentSession annotation = parameter.getParameterAnnotation(CurrentSession.class);
            if (annotation != null && annotation.nullable()) {
                return null;
            }
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (Session.class.isAssignableFrom(parameter.getParameterType())) {
            return session;
        }
        if (SessionId.class.equals(parameter.getParameterType())) {
            return session.id();
        }
        throw unsupported(parameter, "Session 或 SessionId");
    }

    /**
     * 解析当前 Subject 或 Subject 的数值标识参数。
     *
     * @param parameter Controller 方法参数
     * @param call 当前调用
     * @return 当前 Subject、数值标识或注解允许的空值
     */
    private @Nullable Object subject(
            @NotNull MethodParameter parameter,
            @NotNull Call call) {
        Subject subject = call.subject().orElse(null);
        CurrentSubject annotation = parameter.getParameterAnnotation(CurrentSubject.class);
        rejectNullablePrimitive(parameter, annotation != null && annotation.nullable());
        if (subject == null) {
            if (annotation != null && annotation.nullable()) {
                return null;
            }
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (Subject.class.isAssignableFrom(parameter.getParameterType())) {
            return subject;
        }
        if (isLong(parameter)) {
            return subject.subjectId();
        }
        throw unsupported(parameter, "Subject、long 或 Long");
    }

    /**
     * 解析当前 Account 或 Account 的数值标识参数。
     *
     * @param parameter Controller 方法参数
     * @param call 当前调用
     * @return 当前 Account、数值标识或注解允许的空值
     */
    private @Nullable Object account(
            @NotNull MethodParameter parameter,
            @NotNull Call call) {
        Account account = call.account().orElse(null);
        CurrentAccount annotation = parameter.getParameterAnnotation(CurrentAccount.class);
        rejectNullablePrimitive(parameter, annotation != null && annotation.nullable());
        if (account == null) {
            if (annotation != null && annotation.nullable()) {
                return null;
            }
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (Account.class.isAssignableFrom(parameter.getParameterType())) {
            return account;
        }
        if (isLong(parameter)) {
            return account.accountId();
        }
        throw unsupported(parameter, "Account、long 或 Long");
    }

    /**
     * 解析当前 Principal、具体 Principal 实现或 Principal 的数值标识参数。
     *
     * @param parameter Controller 方法参数
     * @param call 当前调用
     * @return 当前 Principal、数值标识或注解允许的空值
     */
    private @Nullable Object principal(
            @NotNull MethodParameter parameter,
            @NotNull Call call) {
        Principal principal = call.principal().orElse(null);
        CurrentPrincipal annotation = parameter.getParameterAnnotation(CurrentPrincipal.class);
        rejectNullablePrimitive(parameter, annotation != null && annotation.nullable());
        if (principal == null) {
            if (annotation != null && annotation.nullable()) {
                return null;
            }
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (isLong(parameter)) {
            return principal.principalId();
        }
        if (!Principal.class.isAssignableFrom(parameter.getParameterType())) {
            throw unsupported(parameter, "Principal、具体 Principal 类型、long 或 Long");
        }
        if (!parameter.getParameterType().isInstance(principal)) {
            throw new IllegalStateException(
                    "参数 "
                            + parameter.getParameterName()
                            + " 要求业务身份类型 "
                            + parameter.getParameterType().getName()
                            + "，当前选择结果为 "
                            + principal.getClass().getName());
        }
        return principal;
    }

    /** 判断参数是否直接接收必填或可空的数值标识。 */
    private static boolean isLong(@NotNull MethodParameter parameter) {
        return long.class.equals(parameter.getParameterType())
                || Long.class.equals(parameter.getParameterType());
    }

    /** 拒绝无法表达空值的 nullable primitive 参数声明。 */
    private static void rejectNullablePrimitive(
            @NotNull MethodParameter parameter,
            boolean nullable) {
        if (nullable && long.class.equals(parameter.getParameterType())) {
            throw new IllegalStateException(
                    "nullable 当前身份参数不能声明为 primitive long: "
                            + parameter.getParameterName());
        }
    }

    /**
     * 创建包含参数名和允许类型的配置错误。
     *
     * @param parameter 声明了不支持类型的 Controller 参数
     * @param supported 当前身份注解允许的类型说明
     * @return 参数声明错误
     */
    private @NotNull IllegalStateException unsupported(
            @NotNull MethodParameter parameter,
            @NotNull String supported) {
        return new IllegalStateException(
                "参数 " + parameter.getParameterName() + " 只能声明为 " + supported);
    }
}
