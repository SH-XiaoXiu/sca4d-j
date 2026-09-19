package com.sca4d.core.spring.identity;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 执行 {@link CurrentPrincipal} 声明的请求阶段 Principal 选择。 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public final class PrincipalSelectionAspect {

    private static final Logger LOG = LoggerFactory.getLogger(PrincipalSelectionAspect.class);

    private final ApplicationContext applicationContext;

    private final PrincipalSelectionInputResolver inputResolver;

    private final PrincipalSelectionInvoker invoker;

    /**
     * 创建业务身份选择切面。
     *
     * @param applicationContext 提供具体 Principal 选择器的 Spring 容器
     * @param inputResolver HTTP 选择输入解析器
     * @param invoker 类型化选择器调用器
     */
    public PrincipalSelectionAspect(
            ApplicationContext applicationContext,
            PrincipalSelectionInputResolver inputResolver,
            PrincipalSelectionInvoker invoker) {
        this.applicationContext = applicationContext;
        this.inputResolver = inputResolver;
        this.invoker = invoker;
    }

    /**
     * 在 Controller 执行前确定当前业务身份。
     *
     * @param joinPoint 即将执行的 Controller 方法
     * @return Controller 方法结果
     * @throws Throwable Principal 选择或业务调用失败时抛出
     */
    @Around("@annotation(com.sca4d.core.spring.web.annotation.CurrentPrincipal)"
            + " || @within(com.sca4d.core.spring.web.annotation.CurrentPrincipal)"
            + " || execution(* *(.., @com.sca4d.core.spring.web.annotation.CurrentPrincipal (*), ..))")
    public @Nullable Object select(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        CurrentPrincipal declaration = CurrentPrincipalDeclaration.find(
                signature.getMethod(),
                signature.getDeclaringType());
        if (declaration == null) {
            return joinPoint.proceed();
        }

        Call call = CallHolder.current();
        if (!call.isPrincipalAssessed()) {
            // 只在一次调用中选择一次 Principal；后续切面和业务方法复用 Call 中的选择结果。
            PrincipalSelection<?> selection = CurrentPrincipalDeclaration.selection(
                    declaration,
                    applicationContext);
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes == null) {
                throw new IllegalStateException("业务身份选择只能在 HTTP 请求中执行");
            }
            HttpServletRequest request = attributes.getRequest();
            PrincipalSelectionInput input = inputResolver.resolve(
                    signature.getMethod(),
                    joinPoint.getArgs(),
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
                        "业务身份已选择 | callId={} principalId={} selector={}",
                        call.callId(),
                        principal.principalId(),
                        selection.getClass().getName());
            } else if (declaration.principalRequired()) {
                throw new IllegalStateException("当前接口必须确定业务身份");
            }
        }
        // Principal 已写入 Call 后才继续 Controller，权限判断不会重新选择业务身份。
        return joinPoint.proceed();
    }
}
