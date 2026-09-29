package com.sca4d.core.api.call;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 保存一次被声明式调用规则包围的方法执行、身份阶段状态和预提交条件。 */
public class CallExecution {

    private final Call call;

    private final Class<?> declaringType;

    private final Method method;

    private final Object[] arguments;

    private final Type resultType;

    private final Map<Class<? extends Annotation>, List<? extends Annotation>> methodDeclarations = new LinkedHashMap<>();

    private final Map<Class<? extends Annotation>, List<? extends Annotation>> typeDeclarations = new LinkedHashMap<>();

    private final List<CallLifecycle.PreCommit> preCommitConditions = new ArrayList<>();

    private @Nullable CallOutcome outcome;

    private boolean invoking;

    private boolean principalRequired;

    private boolean started;

    /** 拒绝同一方法执行被生命周期重复使用。 */
    public void beginExecution() {
        if (started || outcome != null) {
            throw new IllegalStateException("同一方法执行只能进入生命周期一次");
        }
        started = true;
    }

    /** 保存执行链返回的结果；生命周期负责唯一写入，审计读取同一结果。 */
    public void recordOutcome(CallOutcome outcome) {
        this.outcome = Objects.requireNonNull(outcome, "调用结果不能为空");
    }

    /** 查询有效声明；方法声明优先于类型声明。 */
    @Contract(pure = true)
    public <A extends Annotation> @NotNull Optional<A> declaration(@NotNull Class<A> type) {
        return declarations(type).stream().findFirst();
    }

    /** 返回从方法到类型排列的全部声明，包括重复注解；相同声明只保留一次。 */
    @Contract(pure = true)
    public <A extends Annotation> @NotNull @Unmodifiable List<A> declarations(@NotNull Class<A> type) {
        Objects.requireNonNull(type);
        List<A> result = new ArrayList<>();
        List<? extends Annotation> methods = methodDeclarations.get(type);
        List<? extends Annotation> types = typeDeclarations.get(type);
        for (Annotation annotation : methods == null ? Arrays.asList(method.getAnnotationsByType(type)) : methods) {
            result.add(type.cast(annotation));
        }
        for (Annotation annotation : types == null ? Arrays.asList(declaringType.getAnnotationsByType(type)) : types) {
            A value = type.cast(annotation);
            if (!result.contains(value)) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    /** 保存入口解析的声明，保留方法与类型的优先顺序；同一注解类型只能登记一次。 */
    @Contract(mutates = "this")
    public <A extends Annotation> void recordDeclarations(
            @NotNull Class<A> type,
            @NotNull List<A> methods,
            @NotNull List<A> types) {
        Objects.requireNonNull(type);
        if (methodDeclarations.containsKey(type)) {
            throw new IllegalStateException("同一执行不能重复登记注解类型: " + type.getName());
        }
        methodDeclarations.put(type, List.copyOf(methods));
        typeDeclarations.put(type, List.copyOf(types));
    }

    /**
     * 创建一次方法执行；入口可以补充接口方法及组合注解的声明。
     *
     * @param call 当前往返调用
     * @param declaringType 声明业务方法的具体类型
     * @param method 被调用方法
     * @param arguments 方法实参
     * @param resultType 业务返回类型
     */
    public CallExecution(
            Call call,
            Class<?> declaringType,
            Method method,
            Object[] arguments,
            Type resultType) {
        this.call = Objects.requireNonNull(call, "调用不能为空");
        this.declaringType = Objects.requireNonNull(declaringType, "业务类型不能为空");
        this.method = Objects.requireNonNull(method, "业务方法不能为空");
        this.arguments = Arrays.copyOf(Objects.requireNonNull(arguments, "业务方法实参不能为空"), arguments.length);
        this.resultType = Objects.requireNonNull(resultType, "业务返回类型不能为空");
    }

    /** 标记当前执行必须在主体阶段完成业务主体选择。 */
    public void requirePrincipal() {
        principalRequired = true;
    }

    /** 在主体阶段确认已经完成业务主体选择。 */
    public void verifyPrincipal() {
        if (principalRequired && !call.isPrincipalAssessed()) {
            throw new IllegalStateException("当前调用必须在执行前完成业务主体选择");
        }
    }

    /** 登记业务方法成功和事务提交前必须满足的条件。 */
    public void addPreCommit(CallLifecycle.PreCommit condition) {
        preCommitConditions.add(Objects.requireNonNull(condition, "预提交条件不能为空"));
    }

    /** 返回指定类型的预提交条件，供所属功能读取和消费；不能修改条件集合。 */
    @Contract(pure = true)
    public <C extends CallLifecycle.PreCommit> @NotNull @Unmodifiable List<C> preCommitConditions(
            @NotNull Class<C> type) {
        Objects.requireNonNull(type);
        return preCommitConditions.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** 校验实际业务方法成功前必须满足的全部预提交条件。 */
    public void verifyPreCommit() {
        preCommitConditions.forEach(CallLifecycle.PreCommit::verifySatisfied);
    }

    /** 在只读或读写事务提交前校验适用于该事务的预提交条件。 */
    public void verifyPreCommitBeforeCommit(boolean readOnly) {
        if (!invoking) {
            return;
        }
        preCommitConditions.stream()
                .filter(condition -> !readOnly || condition.appliesToReadOnlyTransaction())
                .forEach(CallLifecycle.PreCommit::verifySatisfied);
    }

    /** 标记即将进入实际业务方法。 */
    public void beginInvocation() {
        invoking = true;
    }

    /** 标记实际业务方法已经离开。 */
    public void endInvocation() {
        invoking = false;
    }

    /** @return 当前往返调用 */
    @Contract(pure = true)
    public Call call() {
        return call;
    }

    /** @return 声明业务方法的具体类型 */
    @Contract(pure = true)
    public Class<?> declaringType() {
        return declaringType;
    }

    /** @return 被调用方法 */
    @Contract(pure = true)
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "CallExecution 的公开契约需要向声明解析器提供原始 Method")
    public Method method() {
        return method;
    }

    /** @return 方法实参副本 */
    @Contract(pure = true)
    public Object[] arguments() {
        return Arrays.copyOf(arguments, arguments.length);
    }

    /** @return 业务返回类型 */
    @Contract(pure = true)
    public Type resultType() {
        return resultType;
    }

    /** 返回具体类型和完整参数类型组成的稳定签名，供限流范围及幂等指纹使用。 */
    public String methodSignature() {
        StringBuilder signature = new StringBuilder(declaringType.getName()).append('#')
                .append(method.getName())
                .append('(');
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int index = 0; index < parameterTypes.length; index++) {
            if (index > 0) {
                signature.append(',');
            }
            signature.append(parameterTypes[index].getName());
        }
        return signature.append(')').toString();
    }

    /**
     * 返回当前方法执行结果。
     *
     * @return 已经得到结果时返回结果，否则为空
     */
    @Contract(pure = true)
    public Optional<CallOutcome> outcome() {
        return Optional.ofNullable(outcome);
    }

}
