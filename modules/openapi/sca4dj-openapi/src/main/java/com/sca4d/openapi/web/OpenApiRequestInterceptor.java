package com.sca4d.openapi.web;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.web.HttpCallHandlerInterceptor;
import com.sca4d.openapi.web.annotation.OpenApiRoute;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.Objects;

/** 在普通 Spring MVC Handler 与 Core 权限切面之间检查 OpenAPI Client 身份及开放 Operation。 */
public class OpenApiRequestInterceptor extends HttpCallHandlerInterceptor {

    private final @NotNull OpenApiClientOperationChecker checker;

    public OpenApiRequestInterceptor(@NotNull OpenApiClientOperationChecker checker) {
        this.checker = Objects.requireNonNull(checker, "OpenAPI Client Operation 检查器不能为空");
    }

    /** 检查路由是否由有效 Application Client 调用并开放了目标 Operation。 */
    @Override
    protected boolean preHandleWithCall(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
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
        checker.verifyAllowed(call, TreeCode.ofColon(route.operation()));
        return true;
    }
}
