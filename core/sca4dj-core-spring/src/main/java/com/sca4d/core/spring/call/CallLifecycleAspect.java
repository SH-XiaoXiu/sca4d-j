package com.sca4d.core.spring.call;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.spring.web.RequestVersionConditions;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.Objects;

/** 把 Core 声明和 `@IfMatch` 方法交给固定 `CallLifecycle`。 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public class CallLifecycleAspect {

    private static final String CALL_DECLARATIONS = "@annotation(com.sca4d.core.api.annotation.AnonymousAccess)"
            + " || @within(com.sca4d.core.api.annotation.AnonymousAccess)"
            + " || @annotation(com.sca4d.core.api.annotation.RequirePermission)"
            + " || @within(com.sca4d.core.api.annotation.RequirePermission)"
            + " || @annotation(com.sca4d.core.api.annotation.RequirePermissions)"
            + " || @within(com.sca4d.core.api.annotation.RequirePermissions)"
            + " || @annotation(com.sca4d.core.api.annotation.Audit)"
            + " || @within(com.sca4d.core.api.annotation.Audit)"
            + " || @annotation(com.sca4d.core.api.annotation.RateLimited)"
            + " || @within(com.sca4d.core.api.annotation.RateLimited)"
            + " || @annotation(com.sca4d.core.api.annotation.Idempotent)"
            + " || @within(com.sca4d.core.api.annotation.Idempotent)"
            + " || @annotation(com.sca4d.core.api.annotation.Locked)"
            + " || @within(com.sca4d.core.api.annotation.Locked)"
            + " || @annotation(com.sca4d.core.spring.web.annotation.IfMatch)";

    private final CallLifecycle lifecycle;

    private final RequestVersionConditions versionConditions;

    /**
     * 创建声明式调用生命周期切面。
     *
     * @param lifecycle Core 固定调用生命周期
     * @param versionConditions 当前 HTTP 请求的版本前置条件
     */
    public CallLifecycleAspect(
            CallLifecycle lifecycle,
            RequestVersionConditions versionConditions) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "调用生命周期不能为空");
        this.versionConditions = Objects.requireNonNull(versionConditions, "HTTP 请求版本条件入口不能为空");
    }

    /**
     * 解析具体方法，绑定执行栈并运行固定生命周期。
     *
     * @param joinPoint Spring 拦截的业务方法
     * @return 业务方法结果
     * @throws Throwable 业务方法或调用生命周期报告的失败
     */
    @Around(CALL_DECLARATIONS)
    public @Nullable Object execute(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object target = joinPoint.getTarget();
        Class<?> targetType = target == null
                ? signature.getDeclaringType()
                : AopUtils.getTargetClass(target);
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), targetType);
        CallExecution execution = new CallExecution(
                CallHolder.current(),
                targetType,
                method,
                joinPoint.getArgs(),
                method.getGenericReturnType());
        CallHolder.push(execution);
        try {
            versionConditions.attach(execution);
            return lifecycle.execute(execution, () -> proceed(joinPoint));
        } catch (WrappedThrowable wrapped) {
            throw wrapped.getCause();
        } finally {
            CallHolder.pop(execution);
        }
    }

    /**
     * 让 AspectJ 的 `Throwable` 通过 `Callable` 的 `Exception` 边界。
     *
     * @param joinPoint Spring 拦截的业务方法
     * @return 业务方法结果
     * @throws Exception 业务方法抛出的受检异常
     */
    private @Nullable Object proceed(ProceedingJoinPoint joinPoint) throws Exception {
        try {
            return joinPoint.proceed();
        } catch (Exception exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new WrappedThrowable(throwable);
        }
    }

    /** 保存不能由 `Callable` 直接声明的非 `Exception` 失败。 */
    private static class WrappedThrowable extends RuntimeException {

        /** @param cause 业务方法抛出的原始失败 */
        private WrappedThrowable(Throwable cause) {
            super(cause);
        }
    }
}
