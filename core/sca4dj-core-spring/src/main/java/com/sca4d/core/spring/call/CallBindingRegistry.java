package com.sca4d.core.spring.call;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallInvoker;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.data.DataValues;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 管理 Call 声明绑定的注册快照、方法匹配和执行附着。 */
final class CallBindingRegistry {

    private final Supplier<Clock> clockSource;

    private final Supplier<List<CallLifecycleAdvisor.AnnotationBinding<?>>> declarationSource;

    private final Supplier<List<CallLifecycleAdvisor.MethodBinding>> methodBindingSource;

    private volatile Registration registration;

    private final ConcurrentMap<BindingKey, List<Binding>> plans = new ConcurrentHashMap<>();

    private final ThreadLocal<Boolean> matching = ThreadLocal.withInitial(() -> false);

    /** 保存直接构造的绑定，适用于独立测试和非 Spring 入口。 */
    CallBindingRegistry(
            List<? extends CallLifecycleAdvisor.AnnotationBinding<?>> declarations,
            List<? extends CallLifecycleAdvisor.MethodBinding> methodBindings,
            Clock clock) {
        this(
                () -> List.copyOf(declarations),
                () -> List.copyOf(methodBindings),
                () -> Objects.requireNonNull(clock));
    }

    /** 保存 Spring 延迟提供者，避免注册阶段提前创建绑定依赖。 */
    CallBindingRegistry(
            ObjectProvider<CallLifecycleAdvisor.AnnotationBinding<?>> declarations,
            ObjectProvider<CallLifecycleAdvisor.MethodBinding> methodBindings,
            ObjectProvider<Clock> clock) {
        this(
                () -> declarations.orderedStream().toList(),
                () -> methodBindings.orderedStream().toList(),
                clock::getObject);
    }

    private CallBindingRegistry(
            Supplier<List<CallLifecycleAdvisor.AnnotationBinding<?>>> declarationSource,
            Supplier<List<CallLifecycleAdvisor.MethodBinding>> methodBindingSource,
            Supplier<Clock> clockSource) {
        this.declarationSource = Objects.requireNonNull(declarationSource);
        this.methodBindingSource = Objects.requireNonNull(methodBindingSource);
        this.clockSource = Objects.requireNonNull(clockSource);
    }

    /** 直接构造 Advisor 时立即读取并校验固定注册，保留原有构造器失败时机。 */
    void initialize() {
        registration();
    }

    /** 判断方法是否声明了 Call，或在直接构造模式下由方法绑定负责。 */
    boolean matches(@NotNull Method method, @NotNull Class<?> targetType) {
        if (method.getDeclaringClass() == Object.class) {
            return false;
        }
        if (matching.get()) {
            return false;
        }
        matching.set(true);
        try {
            return !resolve(method, targetType).isEmpty();
        } catch (BeanCurrentlyInCreationException ignored) {
            // 注册绑定正在创建时，当前 Bean 不应参与代理；绑定创建完成后 Spring 会重新匹配业务 Bean。
            return false;
        } finally {
            matching.remove();
        }
    }

    /** 解析方法的全部绑定，并校验所有带 Call 标记的声明均已注册。 */
    @NotNull
    List<Binding> resolve(@NotNull Method method, @NotNull Class<?> targetType) {
        BindingKey key = new BindingKey(method, targetType);
        return plans.computeIfAbsent(key, ignored -> resolveUncached(method, targetType));
    }

    /** 解析一个尚未缓存的方法绑定计划。 */
    private List<Binding> resolveUncached(Method method, Class<?> targetType) {
        Registration registered = registration();
        Method concrete = concreteMethod(method, targetType);
        validateDeclarations(concrete, registered);
        validateDeclarations(method, registered);
        validateDeclarations(targetType, registered);
        for (var parameter : concrete.getParameters()) {
            validateDeclarations(parameter, registered);
        }
        List<Binding> matched = new ArrayList<>();
        for (CallLifecycleAdvisor.AnnotationBinding<?> declaration : registered.declarations) {
            bind(declaration, concrete, method, targetType).ifPresent(matched::add);
        }
        for (CallLifecycleAdvisor.MethodBinding binding : registered.methodBindings) {
            if (binding.matches(concrete, targetType)) {
                matched.add(
                        new Binding(
                                () -> binding.createCall(
                                        UUID.randomUUID().toString(),
                                        clockSource.get().instant(),
                                        concrete,
                                        targetType),
                                binding::attach));
            }
        }
        return matched;
    }

    /** 创建没有声明提供身份的普通系统 Call。 */
    @NotNull
    Call createCall(@NotNull CallInvoker invoker) {
        return new Call(
                invoker,
                UUID.randomUUID().toString(),
                clockSource.get().instant(),
                DataValues.builder().build(),
                null);
    }

    private static boolean isInterceptedType(Class<? extends Annotation> type) {
        return type == CallLifecycle.Intercepted.class
                || type.getDeclaredAnnotation(CallLifecycle.Intercepted.class) != null;
    }

    /** 将类型化注解和回调固定在一次方法匹配中。 */
    private <A extends Annotation> Optional<Binding> bind(
            CallLifecycleAdvisor.AnnotationBinding<A> declaration,
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
                        () -> declaration.createCall(
                                UUID.randomUUID().toString(),
                                clockSource.get().instant(),
                                effective),
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

    /** 未注册的功能声明直接拒绝创建代理。 */
    private static void validateDeclarations(AnnotatedElement element, Registration registration) {
        MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                .stream()
                .map(MergedAnnotation::getType)
                .filter(type -> !CallLifecycle.Intercepted.class.equals(type))
                .filter(CallBindingRegistry::isInterceptedType)
                .filter(type -> !registration.registeredTypes.contains(type))
                .findFirst()
                .ifPresent(type -> {
                    throw new IllegalArgumentException("调用功能注解没有注册声明: " + type.getName() + " / " + element);
                });
    }

    /** 消除泛型桥接方法，保证参数和返回类型来自具体实现。 */
    static Method concreteMethod(Method method, Class<?> targetType) {
        return BridgeMethodResolver.findBridgedMethod(AopUtils.getMostSpecificMethod(method, targetType));
    }

    /** 第一次匹配时解析并校验全部扩展绑定，之后固定本次应用的注册快照。 */
    private Registration registration() {
        Registration current = registration;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = registration;
            if (current == null) {
                List<CallLifecycleAdvisor.AnnotationBinding<?>> declarations = declarationSource.get();
                Set<Class<? extends Annotation>> types = new HashSet<>();
                for (CallLifecycleAdvisor.AnnotationBinding<?> declaration : declarations) {
                    Class<? extends Annotation> type = Objects.requireNonNull(declaration.annotationType());
                    if (!types.add(type)) {
                        throw new IllegalArgumentException("调用注解存在重复注册: " + type.getName());
                    }
                }
                current = new Registration(
                        declarations,
                        methodBindingSource.get(),
                        Set.copyOf(types));
                registration = current;
            }
            return current;
        }
    }

    /** 保存一次方法匹配的创建 Call 和声明附着动作。 */
    static final class Binding {
        private final Consumer<CallExecution> attach;

        private final Supplier<Optional<Call>> create;

        private Binding(Supplier<Optional<Call>> create, Consumer<CallExecution> attach) {
            this.create = create;
            this.attach = attach;
        }

        Optional<Call> createCall() {
            return create.get();
        }

        void attach(CallExecution execution) {
            attach.accept(execution);
        }
    }

    /** 按原始方法和目标类型区分可复用的绑定计划。 */
    private static final class BindingKey {
        private final Class<?> targetType;

        private final Method method;

        private BindingKey(Method method, Class<?> targetType) {
            this.method = method;
            this.targetType = targetType;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BindingKey key)) {
                return false;
            }
            return method.equals(key.method) && targetType.equals(key.targetType);
        }

        @Override
        public int hashCode() {
            return 31 * method.hashCode() + targetType.hashCode();
        }
    }

    /** 固定一次应用启动时解析出的注解和方法绑定。 */
    private static class Registration {
        private final List<CallLifecycleAdvisor.AnnotationBinding<?>> declarations;

        private final List<CallLifecycleAdvisor.MethodBinding> methodBindings;

        private final Set<Class<? extends Annotation>> registeredTypes;

        private Registration(
                List<CallLifecycleAdvisor.AnnotationBinding<?>> declarations,
                List<CallLifecycleAdvisor.MethodBinding> methodBindings,
                Set<Class<? extends Annotation>> registeredTypes) {
            this.declarations = List.copyOf(declarations);
            this.methodBindings = List.copyOf(methodBindings);
            this.registeredTypes = registeredTypes;
        }
    }
}
