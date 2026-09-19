package com.sca4d.app.server.authentication;

import com.sca4d.app.server.service.ActionAuthenticationService;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationAttemptId;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.spring.call.CallHandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.Objects;

/** 在 Controller 参数绑定前单次消费 `@RequireAuthentication` 声明的动作认证结果。 */
public final class ActionAuthenticationInterceptor extends CallHandlerInterceptor {

    private final ActionAuthenticationService authentications;

    /** 创建动作认证请求拦截器。 */
    public ActionAuthenticationInterceptor(@NotNull ActionAuthenticationService authentications) {
        this.authentications = Objects.requireNonNull(authentications, "动作认证服务不能为空");
    }

    /** 读取当前 Session 和验证请求头，并复核消费结果绑定的动作。 */
    @Override
    protected boolean preHandleWithCall(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler,
            @NotNull Call call) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireAuthentication requirement = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                RequireAuthentication.class);
        if (requirement == null) {
            requirement = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(),
                    RequireAuthentication.class);
        }
        if (requirement == null) {
            return true;
        }
        String verification = request.getHeader(AuthenticationHttpContract.VERIFICATION_HEADER);
        if (verification == null || verification.isBlank()) {
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        authentications.consume(
                new AuthenticationAttemptId(verification),
                call.session()
                        .orElseThrow(
                                () -> new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED)),
                new AuthenticationActionCode(requirement.value()));
        return true;
    }
}
