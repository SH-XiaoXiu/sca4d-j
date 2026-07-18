package com.sca4d.common.concurrent;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * 注解 key 表达式解析器。
 *
 * <p>普通字符串会作为字面量返回；包含 SpEL 变量的字符串会按方法参数上下文解析。
 */
public class SpelKeyEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 解析注解中的字面量或方法参数 SpEL。
     *
     * @param expression 审计、锁、限流或幂等注解声明值
     * @param joinPoint 提供目标对象、最具体方法和已绑定参数的 AOP 调用点
     * @return 空声明或 null 结果共用为空字符串，其他结果为文本表示
     */
    public @NotNull String evaluate(@Nullable String expression, @NotNull ProceedingJoinPoint joinPoint) {
        Object value = evaluateObject(expression, joinPoint, null, null);
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 在业务方法完成后的完整表达式环境中解析任意显式结果。
     *
     * @param expression 注解声明的字面量或 SpEL
     * @param joinPoint 提供目标对象、最具体方法和已绑定参数的 AOP 调用点
     * @param result 业务方法真实返回值；失败时为 null
     * @param error 业务方法原始异常；成功时为 null
     * @return 空声明或表达式 null 结果共用返回 null
     */
    public @Nullable Object evaluateObject(
            @Nullable String expression,
            @NotNull ProceedingJoinPoint joinPoint,
            @Nullable Object result,
            @Nullable Throwable error) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        String text = expression.trim();
        if (!shouldEvaluate(text)) {
            return text;
        }
        return parser.parseExpression(text).getValue(context(joinPoint, result, error));
    }

    /**
     * 判断声明文本是否使用当前解析器支持的 SpEL 入口。
     *
     * @param expression 已去除首尾空白的声明文本
     * @return 包含变量引用、字符串表达式或静态类型调用时返回 true
     */
    private boolean shouldEvaluate(@NotNull String expression) {
        return expression.contains("#") || expression.startsWith("'") || expression.startsWith("T(");
    }

    /**
     * 创建包含目标对象、实参数组、方法名和可发现参数名的 SpEL 上下文。
     *
     * @param joinPoint 当前 AOP 调用点
     * @return 只在本次表达式求值期间使用的上下文
     */
    private @NotNull StandardEvaluationContext context(
            @NotNull ProceedingJoinPoint joinPoint, @Nullable Object result, @Nullable Throwable error) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();
        if (target != null) {
            method = AopUtils.getMostSpecificMethod(method, target.getClass());
        }
        Object[] args = joinPoint.getArgs();
        StandardEvaluationContext context = new StandardEvaluationContext(joinPoint.getTarget());
        context.setVariable("args", args);
        context.setVariable("methodName", method.getName());
        context.setVariable("target", joinPoint.getTarget());
        context.setVariable("result", result);
        context.setVariable("error", error);

        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames == null) {
            parameterNames = signature.getParameterNames();
        }
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        return context;
    }

}
