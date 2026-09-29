package com.sca4d.core.api.call;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

/** 编排方法执行的环绕处理、短路、成功要求及结果记录；入口负责建立 Call 和线程绑定。 */
public class CallLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(CallLifecycle.class);

    /** 最外层的结果记录，必须看见所有拒绝、失败和重放。 */
    public static final TreeCode AUDIT = TreeCode.of("CALL/AUDIT");

    /** 检查声明组合与认证前提。 */
    public static final TreeCode DECLARATION = TreeCode.of("CALL/DECLARATION");

    /** 在权限判断前确认调用主体；入口可以在此锚点之前挂载主体选择。 */
    public static final TreeCode PRINCIPAL = TreeCode.of("CALL/PRINCIPAL");

    /** 解析和判断方法权限。 */
    public static final TreeCode PERMISSION = TreeCode.of("CALL/PERMISSION");

    /** 消耗调用额度。 */
    public static final TreeCode RATE_LIMIT = TreeCode.of("CALL/RATE_LIMIT");

    /** 取得幂等处理权或重放结果。 */
    public static final TreeCode IDEMPOTENCY = TreeCode.of("CALL/IDEMPOTENCY");

    /** 取得覆盖方法及内部事务的锁。 */
    public static final TreeCode LOCK = TreeCode.of("CALL/LOCK");

    /** 在方法正常返回后、锁释放与幂等保存之前检查成功要求。 */
    public static final TreeCode REQUIREMENTS = TreeCode.of("CALL/REQUIREMENTS");

    /** 默认进入顺序；未安装功能的锚点仍然可用于声明依赖。 */
    private static final List<TreeCode> ANCHORS = List.of(
            AUDIT,
            DECLARATION,
            PRINCIPAL,
            PERMISSION,
            RATE_LIMIT,
            IDEMPOTENCY,
            LOCK,
            REQUIREMENTS);

    private final List<Around> arounds;

    /**
     * 注册功能及应用的环绕处理，并补齐没有动作的排序锚点。
     *
     * @param registered 各功能和应用提供的处理；编码唯一，前置及成功校验锚点不能替换
     * @throws IllegalArgumentException 编码重复、依赖缺失、存在依赖环或替换校验锚点时抛出
     */
    public CallLifecycle(@NotNull List<? extends @NotNull Around> registered) {
        List<Around> assembled = new ArrayList<>(Objects.requireNonNull(registered));
        Set<TreeCode> codes = new LinkedHashSet<>();
        for (Around around : assembled) {
            TreeCode code = Objects.requireNonNull(around.code());
            if (code.equals(PRINCIPAL) || code.equals(REQUIREMENTS)) {
                throw new IllegalArgumentException("不能替换调用校验锚点: " + code);
            }
            if (!codes.add(code)) {
                throw new IllegalArgumentException("调用环绕编码重复: " + code);
            }
        }
        for (TreeCode code : ANCHORS) {
            if (!codes.contains(code)) {
                assembled.add(new Anchor(code));
            }
        }
        arounds = sort(assembled);
    }

    /**
     * 同步完成方法执行和各环绕处理；返回 Future 只代表本次调度返回，不延伸事务或锁。
     *
     * @param execution 当前方法的独立执行范围
     * @param target 实际方法；可返回 null
     * @return 实际方法或重放的结果
     * @throws Exception 原执行异常或完成处理失败
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T execute(CallExecution execution, Callable<T> target) throws Exception {
        Objects.requireNonNull(execution).beginExecution();
        Objects.requireNonNull(target);
        CallOutcome outcome = invoke(execution, target, 0);
        execution.recordOutcome(outcome);
        LOG.trace(
                "调用生命周期结束 | callId={} method={} outcome={} completionFailed={}",
                execution.call().callId(),
                execution.method().getName(),
                outcome.status(),
                outcome.completionFailure() != null);
        return (T) value(outcome);
    }

    /** 执行当前环绕层；Next 仅在当前线程和回调范围内有效，禁止重复执行或异步保存。 */
    private CallOutcome invoke(CallExecution execution, Callable<?> target, int index) {
        if (index == arounds.size()) {
            try {
                return CallOutcome.success(target.call());
            } catch (Throwable failure) {
                return failure(failure);
            }
        }
        Around around = arounds.get(index);
        Continuation next = new Continuation(() -> invoke(execution, target, index + 1));
        try {
            CallOutcome outcome = Objects.requireNonNull(
                    around.supports(execution) ? around.around(execution, next) : next.proceed(),
                    "环绕处理不能返回空结果: " + around.code());
            if (next.outcome != null && next.outcome.error() != null && outcome.error() == null) {
                throw new IllegalStateException("环绕处理不能把内层失败改写为成功: " + around.code());
            }
            if (next.outcome != null
                    && next.outcome.completionFailure() != null
                    && outcome.completionFailure() == null) {
                throw new IllegalStateException("环绕处理不能忽略内层完成失败: " + around.code());
            }
            if (next.outcome == null && outcome.status() == CallOutcome.Status.SUCCESS) {
                throw new IllegalStateException("未执行 Next 的结果必须明确标记为重放或拒绝: " + around.code());
            }
            return outcome;
        } catch (Throwable failure) {
            if (next.outcome != null
                    && (next.outcome.error() == failure || next.outcome.completionFailure() == failure)) {
                return next.outcome;
            }
            return next.outcome == null ? failure(failure) : next.outcome.withCompletionFailure(failure);
        } finally {
            next.closed = true;
        }
    }

    /** 业务规则拒绝与技术失败使用互斥结果，保留原始 Throwable。 */
    private static CallOutcome failure(Throwable failure) {
        return failure instanceof BusinessException ? CallOutcome.denied(failure) : CallOutcome.failed(failure);
    }

    /** 穿过 Callable 边界时原样抛出 Exception 和 Error，其他 Throwable 保留为 cause。 */
    private static @Nullable Object value(CallOutcome outcome) throws Exception {
        try {
            return outcome.valueOrThrow();
        } catch (Exception | Error failure) {
            throw failure;
        } catch (Throwable throwable) {
            throw new IllegalStateException("调用抛出了非 Exception/ Error 的 Throwable", throwable);
        }
    }

    /** 根据 before/after 做稳定拓扑排序；只有当前无依赖的节点才比较优先级和编码。 */
    private static List<Around> sort(List<Around> registered) {
        Map<TreeCode, Around> remaining = new LinkedHashMap<>();
        Map<TreeCode, Set<TreeCode>> dependencies = new LinkedHashMap<>();
        for (Around around : registered) {
            TreeCode code = Objects.requireNonNull(around.code());
            if (remaining.putIfAbsent(code, around) != null) {
                throw new IllegalArgumentException("调用环绕编码重复: " + code);
            }
            dependencies.put(code, new LinkedHashSet<>(around.after()));
            if (!ANCHORS.contains(code)) {
                dependencies.get(code).add(AUDIT);
            }
        }
        for (int index = 1; index < ANCHORS.size(); index++) {
            dependencies.get(ANCHORS.get(index)).add(ANCHORS.get(index - 1));
        }
        for (Around around : registered) {
            for (TreeCode code : around.before()) {
                if (!dependencies.containsKey(code)) {
                    throw new IllegalArgumentException("调用环绕引用未知依赖: " + code);
                }
                dependencies.get(code).add(around.code());
            }
        }
        for (Set<TreeCode> codes : dependencies.values()) {
            for (TreeCode code : codes) {
                if (!remaining.containsKey(code)) {
                    throw new IllegalArgumentException("调用环绕引用未知依赖: " + code);
                }
            }
        }
        List<Around> sorted = new ArrayList<>();
        Set<TreeCode> available = new LinkedHashSet<>();
        Comparator<Around> order = Comparator.comparingInt(Around::priority)
                .thenComparing(item -> item.code().toString());
        while (!remaining.isEmpty()) {
            Around next = remaining.values()
                    .stream()
                    .filter(item -> available.containsAll(dependencies.get(item.code())))
                    .min(order)
                    .orElseThrow(() -> new IllegalArgumentException("调用环绕依赖存在环: " + remaining.keySet()));
            sorted.add(next);
            available.add(next.code());
            remaining.remove(next.code());
        }
        return List.copyOf(sorted);
    }

    /** 标记调用入口或功能注解；功能注解必须由所属功能注册，不能静默跳过。 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    public @interface Intercepted {
    }

    /** 包围剩余方法执行；允许明确重放或拒绝，不能隐式吞掉内层失败。 */
    public interface Around {
        /** 返回在生命周期内唯一的稳定编码。 */
        @NotNull
        TreeCode code();

        /** 返回必须位于本层之外的编码，锚点在没有对应注解时仍存在。 */
        default @NotNull Set<TreeCode> after() {
            return Set.of();
        }

        /** 返回必须位于本层之内的编码。 */
        default @NotNull Set<TreeCode> before() {
            return Set.of();
        }

        /** 无依赖关系时，数值越小越先进入。 */
        default int priority() {
            return 0;
        }

        /** 当前方法是否适用；该判断也不得写入共享状态。 */
        default boolean supports(@NotNull CallExecution execution) {
            return true;
        }

        /** 处理本层并返回实际路径，Next 最多调用一次且不能跨线程保存。 */
        @NotNull
        CallOutcome around(@NotNull CallExecution execution, @NotNull Next next) throws Throwable;
    }

    /** 在当前线程继续内层处理，返回成功、重放、拒绝或失败，错误由结果携带。 */
    @FunctionalInterface
    public interface Next {
        /** 执行剩余调用；业务异常归一化为 CallOutcome，不再次抛出。 */
        @NotNull
        CallOutcome proceed();
    }

    /** 保留排序位置，并在身份选择后校验前提、在方法返回后校验成功要求。 */
    private static class Anchor implements Around {
        private final TreeCode code;

        private Anchor(TreeCode code) {
            this.code = code;
        }

        @Override
        public @NotNull TreeCode code() {
            return code;
        }

        @Override
        public @NotNull CallOutcome around(@NotNull CallExecution execution, @NotNull Next next) {
            if (code.equals(PRINCIPAL)) {
                execution.verifyBeforeInvocation();
            }
            if (!code.equals(REQUIREMENTS)) {
                return next.proceed();
            }
            execution.beginInvocation();
            try {
                CallOutcome outcome = next.proceed();
                if (outcome.error() == null) {
                    try {
                        execution.verifyRequirements();
                    } catch (Throwable failure) {
                        return failure(failure);
                    }
                }
                return outcome;
            } finally {
                execution.endInvocation();
            }
        }
    }

    /** 控制 Next 的线程、有效范围和调用次数，并保留内层结果。 */
    private static class Continuation implements Next {
        private final Thread owner = Thread.currentThread();

        private final Next next;

        private boolean called;

        private boolean closed;

        private CallOutcome outcome;

        private Continuation(Next next) {
            this.next = next;
        }

        @Override
        public @NotNull CallOutcome proceed() {
            if (Thread.currentThread() != owner || closed || called) {
                throw new IllegalStateException("Next 只能在当前环绕回调的线程内执行一次");
            }
            called = true;
            outcome = next.proceed();
            return outcome;
        }
    }
}
