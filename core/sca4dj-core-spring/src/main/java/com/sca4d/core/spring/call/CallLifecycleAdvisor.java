package com.sca4d.core.spring.call;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallInvoker;
import com.sca4d.core.api.call.CallLifecycle;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 按各模块注册的注解声明选择方法，建立执行范围并进入唯一 CallLifecycle。
 */
public class CallLifecycleAdvisor extends AbstractPointcutAdvisor {
    private final CallInvoker invoker;

    private final Clock clock;

    private final List<AnnotationBinding<?>> declarations;

    private final List<MethodBinding> methodBindings;

    private final Set<Class<? extends Annotation>> registeredTypes;

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
        this.invoker = Objects.requireNonNull(invoker);
        this.clock = Objects.requireNonNull(clock);
        this.declarations = List.copyOf(declarations);
        this.methodBindings = List.copyOf(methodBindings);
        Set<Class<? extends Annotation>> types = new HashSet<>();
        for (AnnotationBinding<?> declaration : declarations) {
            if (!types.add(Objects.requireNonNull(declaration.annotationType()))) {
                throw new IllegalArgumentException("调用注解存在重复注册: " + declaration.annotationType().getName());
            }
        }
        registeredTypes = Set.copyOf(types);
        pointcut = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(@NotNull Method method, @NotNull Class<?> targetClass) {
                return method.getDeclaringClass() != Object.class && !bindings(method, targetClass).isEmpty();
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
        Method method = concreteMethod(invocation.getMethod(), targetType);
        List<Binding> bindings = bindings(invocation.getMethod(), targetType);
        boolean ownsCall = CallHolder.currentOrNull() == null;
        if (ownsCall) {
            List<Call> supplied = new ArrayList<>();
            for (Binding binding : bindings) {
                binding.create.get().ifPresent(supplied::add);
            }
            if (supplied.size() > 1) {
                throw new IllegalStateException("同一次入口不能由多个声明创建 Call");
            }
            CallHolder.set(
                    (supplied.isEmpty()
                            ? new Call(
                                    invoker,
                                    UUID.randomUUID().toString(),
                                    clock.instant(),
                                    com.sca4d.core.api.data.DataValues.builder().build(),
                                    null)
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
            for (Binding binding : bindings) {
                binding.attach.accept(execution);
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
     * 分别读取实现方法、接口方法及类型声明，以支持 JDK/CGLIB 与桥接方法。
     */
    private List<Binding> bindings(Method method, Class<?> targetType) {
        Method concrete = concreteMethod(method, targetType);
        validateDeclarations(concrete);
        validateDeclarations(method);
        validateDeclarations(targetType);
        for (var parameter : concrete.getParameters()) {
            validateDeclarations(parameter);
        }
        List<Binding> matched = new ArrayList<>();
        for (AnnotationBinding<?> declaration : declarations) {
            bind(declaration, concrete, method, targetType).ifPresent(matched::add);
        }
        for (MethodBinding binding : methodBindings) {
            if (binding.matches(concrete, targetType)) {
                matched.add(
                        new Binding(
                                () -> binding.createCall(
                                        UUID.randomUUID().toString(),
                                        clock.instant(),
                                        concrete,
                                        targetType),
                                binding::attach));
            }
        }
        return matched;
    }

    /**
     * 将类型化注解和回调固定在一次方法匹配中，不进行未经校验的泛型转换。
     */
    private <A extends Annotation> Optional<Binding> bind(
            AnnotationBinding<A> declaration,
            Method concrete,
            Method original,
            Class<?> targetType) {
        Class<A> type = declaration.annotationType();
        List<A> methods = annotations(concrete, type);
        if (methods.isEmpty()) {
            methods = annotations(original, type);
        }
        List<A> types = annotations(targetType, type);
        if (methods.isEmpty() && types.isEmpty()) {
            List<A> parameters = new ArrayList<>();
            for (var parameter : concrete.getParameters()) {
                for (A annotation : annotations(parameter, type)) {
                    if (!parameters.contains(annotation)) {
                        parameters.add(annotation);
                    }
                }
            }
            if (parameters.size() > 1 && !type.isAnnotationPresent(Repeatable.class)) {
                throw new IllegalArgumentException("方法参数存在相互冲突的调用声明: " + concrete + " / " + type.getName());
            }
            methods = List.copyOf(parameters);
        }
        if (methods.isEmpty() && types.isEmpty()) {
            return Optional.empty();
        }
        List<A> methodDeclarations = methods;
        A effective = methods.isEmpty() ? types.getFirst() : methods.getFirst();
        Set<A> attached = new LinkedHashSet<>();
        if (type.isAnnotationPresent(Repeatable.class)) {
            attached.addAll(methods);
            attached.addAll(types);
        } else {
            attached.add(effective);
        }
        return Optional.of(
                new Binding(
                        () -> declaration.createCall(UUID.randomUUID().toString(), clock.instant(), effective),
                        execution -> {
                            execution.recordDeclarations(type, methodDeclarations, types);
                            attached.forEach(annotation -> declaration.attach(execution, annotation));
                        }));
    }

    /** 读取普通或重复声明，同时保留 Spring 的接口、继承和组合注解解析。 */
    private static <A extends Annotation> List<A> annotations(AnnotatedElement element, Class<A> type) {
        if (type.isAnnotationPresent(Repeatable.class)) {
            return List.copyOf(AnnotatedElementUtils.findMergedRepeatableAnnotations(element, type));
        }
        A annotation = AnnotatedElementUtils.findMergedAnnotation(element, type);
        return annotation == null ? List.of() : List.of(annotation);
    }

    /** 未注册的功能声明直接拒绝创建代理，避免删除模块后仍静默匹配受保护方法。 */
    private void validateDeclarations(AnnotatedElement element) {
        MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                .stream()
                .map(MergedAnnotation::getType)
                .filter(type -> type.getDeclaredAnnotation(CallLifecycle.Intercepted.class) != null)
                .filter(type -> !registeredTypes.contains(type))
                .findFirst()
                .ifPresent(type -> {
                    throw new IllegalArgumentException("调用功能注解没有注册声明: " + type.getName() + " / " + element);
                });
    }

    /**
     * 消除泛型桥接方法，保证参数和返回类型来自具体实现。
     */
    private static Method concreteMethod(Method method, Class<?> targetType) {
        return BridgeMethodResolver.findBridgedMethod(AopUtils.getMostSpecificMethod(method, targetType));
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
        /** 判断当前实现方法是否进入该绑定。 */
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
     * 保存一次匹配的类型化回调，离开后不缓存调用状态或实参。
     */
    private static class Binding {
        private final Supplier<Optional<Call>> create;

        private final Consumer<CallExecution> attach;

        private Binding(Supplier<Optional<Call>> create, Consumer<CallExecution> attach) {
            this.create = create;
            this.attach = attach;
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
