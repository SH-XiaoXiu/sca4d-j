package com.sca4d.core.spring.call;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallInvoker;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 按各模块注册的注解声明选择方法，建立执行范围并进入唯一 CallLifecycle。
 */
public class CallLifecycleAdvisor extends AbstractPointcutAdvisor {
    private final CallBindingRegistry bindings;

    private final Supplier<CallInvoker> invokerSource;

    private final Pointcut pointcut;

    private final Advice advice;

    /**
     * 注册注解所属模块的声明；同一个注解类型只能由一个注册认领。
     *
     * @param invoker      已校验依赖的调用执行器
     * @param declarations 模块或应用提供的注解注册，不包含中央固定注解清单
     * @param clock        自主入口建立 Call 使用的时钟
     */
    public CallLifecycleAdvisor(CallInvoker invoker, List<? extends AnnotationBinding<?>> declarations, Clock clock) {
        this(invoker, declarations, List.of(), clock);
    }

    /** 注册注解绑定和非注解方法绑定。 */
    public CallLifecycleAdvisor(
            CallInvoker invoker,
            List<? extends AnnotationBinding<?>> declarations,
            List<? extends MethodBinding> methodBindings,
            Clock clock) {
        this(
                () -> Objects.requireNonNull(invoker),
                new CallBindingRegistry(declarations, methodBindings, clock));
        bindings.initialize();
    }

    /** 使用 Spring 延迟提供者，避免 Advisor 创建时提前实例化调用基础设施。 */
    public CallLifecycleAdvisor(
            ObjectProvider<CallInvoker> invoker,
            ObjectProvider<AnnotationBinding<?>> declarations,
            ObjectProvider<MethodBinding> methodBindings,
            ObjectProvider<Clock> clock) {
        this(
                invoker::getObject,
                new CallBindingRegistry(declarations, methodBindings, clock));
    }

    private CallLifecycleAdvisor(
            Supplier<CallInvoker> invokerSource,
            CallBindingRegistry bindings) {
        this.invokerSource = Objects.requireNonNull(invokerSource);
        this.bindings = Objects.requireNonNull(bindings);
        pointcut = new StaticMethodMatcherPointcut() {
            @Override
            public @NotNull ClassFilter getClassFilter() {
                return targetClass -> !AnnotationBinding.class.isAssignableFrom(targetClass)
                        && !MethodBinding.class.isAssignableFrom(targetClass);
            }

            @Override
            public boolean matches(@NotNull Method method, @NotNull Class<?> targetClass) {
                // Bean 工厂声明用于装配返回对象，创建单例时不能提前进入业务 Call。
                if (method.getDeclaringClass() == Object.class
                        || AnnotatedElementUtils.hasAnnotation(
                                CallBindingRegistry.concreteMethod(method, targetClass),
                                Bean.class)) {
                    return false;
                }
                return bindings.matches(method, targetClass);
            }
        };
        advice = (MethodInterceptor) this::invoke;
        setOrder(Ordered.HIGHEST_PRECEDENCE + 200);
    }

    @Override
    public @NotNull Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public @NotNull Advice getAdvice() {
        return advice;
    }

    /**
     * 为匹配方法建立独立执行范围；任何声明附着或方法失败都清理本次绑定。
     */
    private @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = invocation.getThis();
        Class<?> targetType = target == null
                ? invocation.getMethod().getDeclaringClass()
                : AopUtils.getTargetClass(target);
        Method method = CallBindingRegistry.concreteMethod(invocation.getMethod(), targetType);
        List<CallBindingRegistry.Binding> matchedBindings = this.bindings.resolve(invocation.getMethod(), targetType);
        CallInvoker invoker = invokerSource.get();
        boolean ownsCall = CallHolder.currentOrNull() == null;
        if (ownsCall) {
            List<Call> supplied = new ArrayList<>();
            for (CallBindingRegistry.Binding binding : matchedBindings) {
                binding.createCall().ifPresent(supplied::add);
            }
            if (supplied.size() > 1) {
                throw new IllegalStateException("同一次入口不能由多个声明创建 Call");
            }
            CallHolder.set(
                    (supplied.isEmpty()
                            ? this.bindings.createCall(invoker)
                            : supplied.getFirst().withInvoker(invoker)));
        }
        CallExecution execution = null;
        try {
            CallHolder.current().withInvoker(invoker);
            execution = new CallExecution(
                    CallHolder.current(),
                    targetType,
                    method,
                    invocation.getArguments(),
                    resultType(method));
            CallHolder.push(execution);
            for (CallBindingRegistry.Binding binding : matchedBindings) {
                binding.attach(execution);
            }
            CallPreCommitTransactionListener.track(execution);
            if (CompletionStage.class.isAssignableFrom(method.getReturnType())) {
                return CallHolder.current().invokeAsync(execution, () -> {
                    try {
                        Object result = proceed(invocation);
                        if (!(result instanceof CompletionStage<?> stage)) {
                            return java.util.concurrent.CompletableFuture.failedFuture(
                                    new IllegalStateException("异步方法必须返回 CompletionStage: " + method));
                        }
                        @SuppressWarnings("unchecked")
                        CompletionStage<Object> typed = (CompletionStage<Object>) stage;
                        return typed;
                    } catch (Exception failure) {
                        return java.util.concurrent.CompletableFuture.failedFuture(failure);
                    }
                });
            }
            return CallHolder.current().invoke(execution, () -> proceed(invocation));
        } catch (WrappedThrowable failure) {
            throw failure.getCause();
        } finally {
            if (execution != null) {
                CallHolder.pop(execution);
            }
            if (ownsCall) {
                CallHolder.clear();
            }
        }
    }

    /** 异步方法保存完成值的声明类型，供幂等结果编码和重放使用。 */
    private static Type resultType(Method method) {
        Type declared = method.getGenericReturnType();
        if (!CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            return declared;
        }
        if (declared instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length == 1) {
            return parameterized.getActualTypeArguments()[0];
        }
        throw new IllegalStateException("异步方法必须声明 CompletionStage 的结果类型: " + method);
    }

    /**
     * 原样传递普通异常及 Error，只包装 Callable 无法声明的其他 Throwable。
     */
    private static Object proceed(MethodInvocation invocation) throws Exception {
        try {
            return invocation.proceed();
        } catch (Exception | Error exception) {
            throw exception;
        } catch (Throwable failure) {
            throw new WrappedThrowable(failure);
        }
    }

    /**
     * 由模块注册注解匹配、入口身份与执行要求；不在绑定回调中执行业务动作。
     */
    public interface AnnotationBinding<A extends Annotation> {
        /**
         * 返回本模块负责的运行时注解类型。
         */
        @NotNull
        Class<A> annotationType();

        /**
         * 没有既有 Call 的自主入口可创建固定身份；普通绑定返回空，不能覆盖已有身份。
         */
        default @NotNull Optional<Call> createCall(String callId, Instant startedAt, A annotation) {
            return Optional.empty();
        }

        /**
         * 向方法执行附着解析后的要求；权限、锁等实际动作由环绕处理执行。
         */
        default void attach(@NotNull CallExecution execution, @NotNull A annotation) {
        }
    }

    /** 通过方法元数据进入 Call 的绑定方式；不要求任何注解。 */
    public interface MethodBinding {
        /**
         * 判断当前实现方法是否进入该绑定。
         *
         * <p>实现必须只依赖方法和目标类型。注册表会缓存判断结果，因此不能读取参数、当前 Call、线程状态或外部存储。
         */
        boolean matches(@NotNull Method method, @NotNull Class<?> targetType);

        /** 没有现有 Call 时可创建自主入口；普通方法绑定返回空。 */
        default @NotNull Optional<Call> createCall(
                @NotNull String callId,
                @NotNull Instant startedAt,
                @NotNull Method method,
                @NotNull Class<?> targetType) {
            return Optional.empty();
        }

        /** 向匹配的方法执行附着声明或输入。 */
        default void attach(@NotNull CallExecution execution) {
        }
    }

    /**
     * 保留标准 Callable 不能声明的原始 Throwable。
     */
    private static class WrappedThrowable extends RuntimeException {
        private WrappedThrowable(Throwable failure) {
            super(failure);
        }
    }
}
