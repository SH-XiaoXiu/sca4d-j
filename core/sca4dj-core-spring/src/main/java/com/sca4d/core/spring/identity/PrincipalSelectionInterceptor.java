package com.sca4d.core.spring.identity;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.spring.web.HttpCallHandlerInterceptor;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;

import java.util.Objects;

/** 在 MVC 参数绑定前执行不依赖 request body 和方法参数的业务身份选择。 */
public class PrincipalSelectionInterceptor extends HttpCallHandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(PrincipalSelectionInterceptor.class);

    private final @NotNull ApplicationContext applicationContext;

    private final @NotNull PrincipalSelectionInputResolver inputResolver;

    private final @NotNull PrincipalSelectionInvoker invoker;

    /**
     * 创建业务身份预选择拦截器。
     *
     * @param applicationContext 提供具体选择器的 Spring 容器
     * @param inputResolver HTTP 选择输入解析器
     * @param invoker 类型化选择器调用器
     */
    public PrincipalSelectionInterceptor(
            @NotNull ApplicationContext applicationContext,
            @NotNull PrincipalSelectionInputResolver inputResolver,
            @NotNull PrincipalSelectionInvoker invoker) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "Spring 容器不能为空");
        this.inputResolver = Objects.requireNonNull(inputResolver, "业务身份选择输入解析器不能为空");
        this.invoker = Objects.requireNonNull(invoker, "业务身份选择调用器不能为空");
    }

    /**
     * 在 Controller 参数解析前完成声明为可提前执行的业务身份选择。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler Spring MVC 已选择的 Handler
     * @param call 当前请求唯一的调用
     * @return 始终为 {@code true}，表示继续执行后续拦截器和 Controller；未选出业务身份时由 {@link PrincipalSelectionCallInterceptor} 在参数绑定后继续处理
     * @throws BusinessException 必须选择 Principal 但请求尚未认证时抛出
     * @throws IllegalStateException 无法确定必须存在的 Principal 时抛出
     */
    @Override
    protected boolean preHandleWithCall(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler,
            @NotNull Call call) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        CurrentPrincipal declaration = CurrentPrincipalDeclaration.find(
                handlerMethod.getMethod(),
                handlerMethod.getBeanType());
        if (declaration == null) {
            return true;
        }
        PrincipalSelection<?> selection = CurrentPrincipalDeclaration.selection(
                declaration,
                applicationContext);
        if (!selection.supportsSelectionBeforeArgumentBinding()) {
            return true;
        }

        if (declaration.principalRequired() && !call.isAuthenticationAssessed()) {
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (!call.isPrincipalAssessed()) {
            PrincipalSelectionInput input = inputResolver.resolveBeforeArgumentBinding(
                    handlerMethod.getMethod(),
                    request);
            Principal principal = invoker.select(
                    selection,
                    call.session().orElse(null),
                    call.subject().orElse(null),
                    call.account().orElse(null),
                    input);
            if (principal != null) {
                call.selectPrincipal(principal);
                MDC.put("principalId", Long.toString(principal.principalId()));
                LOG.trace(
                        "业务身份已提前选择 | callId={} principalId={} selector={}",
                        call.callId(),
                        principal.principalId(),
                        selection.getClass().getName());
            } else if (declaration.principalRequired()) {
                throw new IllegalStateException("当前接口必须确定业务身份");
            }
        }
        return true;
    }
}
