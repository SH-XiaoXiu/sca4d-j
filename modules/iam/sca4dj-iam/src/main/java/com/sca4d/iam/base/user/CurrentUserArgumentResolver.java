package com.sca4d.iam.base.user;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.spring.call.CallHolder;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 从当前请求的 Session 注入 USER，并把 USER 唯一的 PlatformPrincipal 绑定到 `Call`。 */
public final class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /** 创建无外部依赖的当前 USER 参数解析器。 */
    public CurrentUserArgumentResolver() {
    }

    /**
     * 判断参数是否声明 {@link CurrentUser}。
     *
     * @param parameter Controller 方法参数
     * @return 参数声明 {@link CurrentUser} 时返回 {@code true}
     */
    @Override
    @CheckReturnValue
    public boolean supportsParameter(@NotNull MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    /**
     * 注入当前 USER 或 USER 的 Long 标识，并为本次调用绑定 USER 的 PlatformPrincipal。
     *
     * @param parameter Controller 方法参数
     * @param modelAndViewContainer 当前 MVC 容器
     * @param webRequest 当前 HTTP 请求
     * @param binderFactory 数据绑定工厂
     * @return 当前 USER 或 USER 的 Long 标识
     */
    @Override
    @CheckReturnValue
    public @NotNull Object resolveArgument(
            @NotNull MethodParameter parameter,
            @Nullable ModelAndViewContainer modelAndViewContainer,
            @NotNull NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        Class<?> parameterType = parameter.getParameterType();
        if (!User.class.equals(parameterType) && !Long.class.equals(parameterType)) {
            throw new IllegalStateException("@CurrentUser 参数只能声明为 User 或 Long");
        }

        Call call = CallHolder.current();
        Session session = call.session()
                .orElseThrow(() -> new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED));
        User user = User.from(session);
        call.selectPrincipal(session.principalById(user.principalId()));
        MDC.put("principalId", Long.toString(user.principalId()));
        return User.class.equals(parameterType) ? user : user.userId();
    }
}
