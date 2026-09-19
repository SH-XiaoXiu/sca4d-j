package com.sca4d.openapi.web;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.spring.call.CallHandlerInterceptor;
import com.sca4d.openapi.web.annotation.OpenApiRoute;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.Objects;

/** 在普通 Spring MVC Handler 与 Core 权限切面之间检查 OpenAPI Client 身份及开放 Operation。 */
public final class OpenApiRequestInterceptor extends CallHandlerInterceptor {

    private final @NotNull OpenApiClientOperationChecker checker;

    /** 创建 OpenAPI 请求拦截器。 */
    public OpenApiRequestInterceptor(@NotNull OpenApiClientOperationChecker checker) {
        this.checker = Objects.requireNonNull(checker, "OpenAPI Client Operation 检查器不能为空");
    }

    /** 检查路由是否由有效 Application Client 调用并开放了目标 Operation。 */
    @Override
    protected boolean preHandleWithCall(
            @NotNull jakarta.servlet.http.HttpServletRequest request,
            @NotNull jakarta.servlet.http.HttpServletResponse response,
            @NotNull Object handler,
            @NotNull Call call) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        OpenApiRoute route = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                OpenApiRoute.class);
        if (route == null) {
            return true;
        }
        if (!call.isAuthenticationAssessed()
                || !call.subject()
                        .map(subject -> subject.type() == SubjectType.APPLICATION)
                        .orElse(false)) {
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        checker.verifyAllowed(call, route.operation());
        return true;
    }
}
