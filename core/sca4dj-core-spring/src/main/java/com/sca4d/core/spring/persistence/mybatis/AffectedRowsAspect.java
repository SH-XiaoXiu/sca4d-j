package com.sca4d.core.spring.persistence.mybatis;

import com.sca4d.core.api.persistence.AffectedRows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

/** 执行 Repository 的 `@AffectedRows` 声明，并在方法返回前解释其唯一一条 DML 结果。 */
@Aspect
public final class AffectedRowsAspect {

    /**
     * 在 Repository 方法执行期间建立影响行数声明。
     *
     * @param joinPoint Spring 拦截的 Repository 方法
     * @return Repository 方法结果
     * @throws Throwable Repository 方法、声明检查或影响行数解释报告的失败
     */
    @Around("@annotation(com.sca4d.core.api.persistence.AffectedRows)")
    public @Nullable Object interpret(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object target = joinPoint.getTarget();
        Class<?> targetType = target == null
                ? signature.getDeclaringType()
                : AopUtils.getTargetClass(target);
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), targetType);
        AffectedRows declaration = AnnotatedElementUtils.findMergedAnnotation(method, AffectedRows.class);
        if (declaration == null) {
            throw new IllegalStateException("无法读取 Repository 方法的 @AffectedRows 声明: " + method);
        }

        AffectedRowsInvocation.open(declaration);
        try {
            Object result = joinPoint.proceed();
            AffectedRowsInvocation current = AffectedRowsInvocation.currentOrNull();
            if (current == null) {
                throw new IllegalStateException("@AffectedRows Repository 方法执行期间声明丢失: " + method);
            }
            current.complete();
            return result;
        } finally {
            AffectedRowsInvocation.clear();
        }
    }
}
