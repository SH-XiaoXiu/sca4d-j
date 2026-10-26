package com.sca4d.core.api.call;

import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.exception.CoreApiException;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** 执行已经配置的 Call 环绕处理；生命周期阶段由 {@link CallLifecycle} 表达。 */
public class CallInvoker {
    private static final Logger LOG = LoggerFactory.getLogger(CallInvoker.class);

    private final List<CallLifecycleConfiguration.Registration> registrations;

    /**
     * 注册功能及应用的环绕处理，并补齐没有动作的排序锚点。
     *
     * @param registered 各功能和应用提供的处理；编码唯一，前置及成功校验锚点不能替换
     * @throws IllegalArgumentException 编码重复、依赖缺失、存在依赖环或替换校验锚点时抛出
     */
    public CallInvoker(@NotNull List<? extends CallLifecycle.Participant> registered) {
        this(new CallLifecycleConfiguration(registered));
    }

    /** 使用已经完成内置顺序和扩展依赖配置的调用执行器。 */
    public CallInvoker(@NotNull CallLifecycleConfiguration configuration) {
        CallLifecycleConfiguration checked = Objects.requireNonNull(configuration);
        checked.applyCoreOrder();
        registrations = sort(checked.registrations());
    }

    /**
     * 同步完成方法执行和各环绕处理，结束后才返回最终结果。
     *
     * @param execution 当前方法的独立执行范围
     * @param target 实际方法；可返回 null
     * @return 实际方法或重放的结果
     * @throws Exception 原执行异常或完成处理失败
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T execute(CallExecution execution, Callable<T> target) throws Exception {
        // 在入口线程选定参与者并登记提交后回调，再开始方法执行。
        CallExecution checkedExecution = Objects.requireNonNull(execution);
        List<CallLifecycleConfiguration.Registration> active = active(checkedExecution);
        registerAfterCommit(checkedExecution, active);
        checkedExecution.beginExecution();
        Objects.requireNonNull(target);
        // 环绕处理和目标方法返回后，依次执行结果阶段并保存最终结果。
        CallOutcome outcome = complete(execution, active, invoke(execution, target, active, 0));
        checkedExecution.recordOutcome(outcome);
        LOG.trace(
                "调用生命周期结束 | callId={} method={} outcome={} completionFailed={}",
                execution.call().callId(),
                execution.invocationName(),
                outcome.status(),
                outcome.completionFailure() != null);
        return (T) value(outcome);
    }

    /**
     * 异步完成方法执行和各环绕处理；完成前不保存幂等结果、释放异步锁或记录最终审计。
     *
     * @param execution 当前方法的独立执行范围
     * @param target 返回业务最终结果的异步方法
     * @return 业务结果或重放结果的完成阶段
     */
    @SuppressWarnings("unchecked")
    public <T> @NotNull CompletionStage<T> executeAsync(
            @NotNull CallExecution execution,
            @NotNull Supplier<? extends @NotNull CompletionStage<T>> target) {
        CallExecution checkedExecution = Objects.requireNonNull(execution);
        List<CallLifecycleConfiguration.Registration> active = active(checkedExecution);
        registerAfterCommit(checkedExecution, active);
        checkedExecution.beginExecution();
        Objects.requireNonNull(target);
        CompletionStage<CallOutcome> completed = invokeAsync(execution, target, active, 0)
                .handle((outcome, failure) -> {
                    CallOutcome finalOutcome = complete(
                            execution,
                            active,
                            failure == null ? outcome : failure(unwrap(failure)));
                    checkedExecution.recordOutcome(finalOutcome);
                    LOG.trace(
                            "异步调用生命周期结束 | callId={} method={} outcome={} completionFailed={}",
                            execution.call().callId(),
                            execution.invocationName(),
                            finalOutcome.status(),
                            finalOutcome.completionFailure() != null);
                    return finalOutcome;
                });
        return completed.thenApply(outcome -> {
            try {
                return (T) value(outcome);
            } catch (Exception | Error failure) {
                throw new CompletionException(failure);
            }
        });
    }

    /** 在入口线程选定参与者，完成线程不再查询线程局部状态。 */
    private List<CallLifecycleConfiguration.Registration> active(CallExecution execution) {
        return registrations.stream()
                .filter(registration -> registration.interceptor().supports(execution))
                .toList();
    }

    /** 提交后通知在方法执行前登记，由事务监听器按提交结果触发。 */
    private void registerAfterCommit(
            CallExecution execution,
            List<CallLifecycleConfiguration.Registration> active) {
        for (CallLifecycleConfiguration.Registration registration : active) {
            if (registration.interceptor() instanceof CallLifecycle.AfterCommit callback) {
                execution.addAfterCommit(callback);
            }
        }
    }

    /** 执行当前环绕层；Next 仅在当前线程和回调范围内有效，禁止重复执行或异步保存。 */
    private CallOutcome invoke(
            CallExecution execution,
            Callable<?> target,
            List<CallLifecycleConfiguration.Registration> active,
            int index) {
        if (index == active.size()) {
            try {
                execution.beginInvocation();
                try {
                    CallOutcome outcome = CallOutcome.success(target.call());
                    if (outcome.error() == null) {
                        try {
                            execution.verifyPreCommit();
                        } catch (Throwable failure) {
                            return failure(failure);
                        }
                    }
                    return outcome;
                } finally {
                    execution.endInvocation();
                }
            } catch (Throwable failure) {
                return failure(failure);
            }
        }
        CallLifecycle.Participant participant = active.get(index).interceptor();
        if (participant instanceof CallLifecycle.Before before) {
            try {
                before.before(execution);
                return invoke(execution, target, active, index + 1);
            } catch (Throwable failure) {
                return failure(failure);
            }
        }
        if (participant instanceof CallLifecycle.AfterReturning
                || participant instanceof CallLifecycle.AfterThrowing
                || participant instanceof CallLifecycle.After
                || participant instanceof CallLifecycle.Completion
                || participant instanceof CallLifecycle.AfterCommit) {
            return invoke(execution, target, active, index + 1);
        }
        if (!(participant instanceof CallLifecycle.Around around)) {
            throw new IllegalStateException("不支持的调用生命周期参与者: " + participant.code());
        }
        Continuation next = new Continuation(() -> invoke(execution, target, active, index + 1));
        try {
            CallOutcome outcome = Objects.requireNonNull(
                    around.around(execution, next),
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

    /** 所有 Around 完成后按配置顺序触发结果阶段，Completion 最后清理。 */
    private CallOutcome complete(
            CallExecution execution,
            List<CallLifecycleConfiguration.Registration> active,
            CallOutcome outcome) {
        CallOutcome current = Objects.requireNonNull(outcome, "调用结果不能为空");
        for (CallLifecycleConfiguration.Registration registration : active) {
            CallLifecycle.Participant participant = registration.interceptor();
            CallOutcome snapshot = current;
            if (participant instanceof CallLifecycle.AfterReturning afterReturning
                    && snapshot.error() == null
                    && snapshot.completionFailure() == null) {
                current = callback(snapshot, () -> afterReturning.afterReturning(execution, snapshot));
            } else if (participant instanceof CallLifecycle.AfterThrowing afterThrowing
                    && (snapshot.error() != null || snapshot.completionFailure() != null)) {
                current = callback(snapshot, () -> afterThrowing.afterThrowing(execution, snapshot));
            } else if (participant instanceof CallLifecycle.After after) {
                current = callback(snapshot, () -> after.after(execution, snapshot));
            } else if (participant instanceof CallLifecycle.Completion completion) {
                current = callback(snapshot, () -> completion.complete(execution, snapshot));
            }
        }
        return current;
    }

    /** 回调失败保留已经确定的业务结果。 */
    private static CallOutcome callback(CallOutcome outcome, ThrowingAction action) {
        try {
            action.run();
            return outcome;
        } catch (Throwable failure) {
            return outcome.withCompletionFailure(failure);
        }
    }

    /** 异步执行当前环绕层，并在最终完成时校验内层失败和成功要求。 */
    private CompletionStage<CallOutcome> invokeAsync(
            CallExecution execution,
            Supplier<? extends CompletionStage<?>> target,
            List<CallLifecycleConfiguration.Registration> active,
            int index) {
        if (index == active.size()) {
            try {
                execution.beginInvocation();
                CompletionStage<?> result = Objects.requireNonNull(target.get(), "异步目标不能返回 null");
                return result.handle((value, failure) -> {
                    try {
                        CallOutcome outcome = failure == null
                                ? CallOutcome.success(value)
                                : failure(unwrap(failure));
                        if (outcome.error() == null) {
                            try {
                                execution.verifyPreCommit();
                            } catch (Throwable preCommitFailure) {
                                return failure(preCommitFailure);
                            }
                        }
                        return outcome;
                    } finally {
                        execution.endInvocation();
                    }
                });
            } catch (Throwable failure) {
                execution.endInvocation();
                return CompletableFuture.completedFuture(failure(failure));
            }
        }
        CallLifecycle.Participant participant = active.get(index).interceptor();
        if (participant instanceof CallLifecycle.Before before) {
            try {
                before.before(execution);
                return invokeAsync(execution, target, active, index + 1);
            } catch (Throwable failure) {
                return CompletableFuture.completedFuture(failure(failure));
            }
        }
        if (participant instanceof CallLifecycle.AfterReturning
                || participant instanceof CallLifecycle.AfterThrowing
                || participant instanceof CallLifecycle.After
                || participant instanceof CallLifecycle.Completion
                || participant instanceof CallLifecycle.AfterCommit) {
            return invokeAsync(execution, target, active, index + 1);
        }
        if (!(participant instanceof CallLifecycle.Around around)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("不支持的调用生命周期参与者: " + participant.code()));
        }
        AsyncContinuation next = new AsyncContinuation(
                () -> invokeAsync(execution, target, active, index + 1));
        CompletionStage<CallOutcome> stage;
        try {
            stage = Objects.requireNonNull(
                    around.aroundAsync(execution, next),
                    "异步环绕处理不能返回空结果: " + around.code());
        } catch (Throwable failure) {
            stage = CompletableFuture.completedFuture(failure(failure));
        }
        return stage.handle((outcome, failure) -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                return next.outcome == null ? failure(cause) : next.outcome.withCompletionFailure(cause);
            }
            if (outcome == null) {
                return failure(new IllegalStateException("异步环绕处理返回空结果: " + around.code()));
            }
            if (next.outcome != null && next.outcome.error() != null && outcome.error() == null) {
                return failure(new IllegalStateException("环绕处理不能把内层失败改写为成功: " + around.code()));
            }
            if (next.outcome != null
                    && next.outcome.completionFailure() != null
                    && outcome.completionFailure() == null) {
                return failure(new IllegalStateException("环绕处理不能忽略内层完成失败: " + around.code()));
            }
            if (next.outcome == null && outcome.status() == CallOutcome.Status.SUCCESS) {
                return failure(new IllegalStateException("未执行 Next 的结果必须明确标记为重放或拒绝: " + around.code()));
            }
            return outcome;
        });
    }

    /** 取出 CompletionException 层层包装的原始失败；其他 Throwable 原样返回。 */
    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
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
            throw new CoreApiException("调用抛出了非 Exception/Error 的 Throwable", throwable);
        }
    }

    /** 根据 before/after 做稳定拓扑排序；只有当前无依赖的节点才比较优先级和编码。 */
    private static List<CallLifecycleConfiguration.Registration> sort(
            List<CallLifecycleConfiguration.Registration> registered) {
        Map<TreeCode, CallLifecycleConfiguration.Registration> remaining = new LinkedHashMap<>();
        Map<TreeCode, Set<TreeCode>> dependencies = new LinkedHashMap<>();
        for (CallLifecycleConfiguration.Registration registration : registered) {
            TreeCode code = Objects.requireNonNull(registration.interceptor().code());
            if (remaining.putIfAbsent(code, registration) != null) {
                throw new IllegalArgumentException("调用环绕编码重复: " + code);
            }
            dependencies.put(code, new LinkedHashSet<>(registration.after()));
        }
        for (CallLifecycleConfiguration.Registration registration : registered) {
            for (TreeCode code : registration.before()) {
                if (!dependencies.containsKey(code)) {
                    throw new IllegalArgumentException("调用环绕引用未知依赖: " + code);
                }
                dependencies.get(code).add(registration.interceptor().code());
            }
        }
        for (CallLifecycleConfiguration.Registration registration : registered) {
            if (registration.lifecycle() == CallLifecycle.AFTER) {
                for (CallLifecycleConfiguration.Registration candidate : registered) {
                    if (candidate.lifecycle() == CallLifecycle.AFTER_RETURNING
                            || candidate.lifecycle() == CallLifecycle.AFTER_THROWING) {
                        dependencies.get(registration.interceptor().code()).add(candidate.interceptor().code());
                    }
                }
            }
            if (registration.lifecycle() == CallLifecycle.COMPLETION) {
                for (CallLifecycleConfiguration.Registration candidate : registered) {
                    if (candidate != registration && candidate.lifecycle() != CallLifecycle.AFTER_COMMIT) {
                        dependencies.get(registration.interceptor().code()).add(candidate.interceptor().code());
                    }
                }
            }
        }
        for (Set<TreeCode> codes : dependencies.values()) {
            for (TreeCode code : codes) {
                if (!remaining.containsKey(code)) {
                    throw new IllegalArgumentException("调用环绕引用未知依赖: " + code);
                }
            }
        }
        List<CallLifecycleConfiguration.Registration> sorted = new ArrayList<>();
        Set<TreeCode> available = new LinkedHashSet<>();
        Comparator<CallLifecycleConfiguration.Registration> order = Comparator
                .comparingInt((CallLifecycleConfiguration.Registration item) -> item.lifecycle().ordinal())
                .thenComparingInt(CallLifecycleConfiguration.Registration::priority)
                .thenComparing(item -> item.interceptor().code().toString());
        while (!remaining.isEmpty()) {
            CallLifecycleConfiguration.Registration next = remaining.values()
                    .stream()
                    .filter(item -> available.containsAll(dependencies.get(item.interceptor().code())))
                    .min(order)
                    .orElseThrow(() -> new IllegalArgumentException("调用环绕依赖存在环: " + remaining.keySet()));
            sorted.add(next);
            available.add(next.interceptor().code());
            remaining.remove(next.interceptor().code());
        }
        return List.copyOf(sorted);
    }

    /** 允许生命周期回调抛出任意原始失败。 */
    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Throwable;
    }

    /** 控制 Next 的线程、有效范围和调用次数，并保留内层结果。 */
    private static class Continuation implements CallLifecycle.Next {
        private final Thread owner = Thread.currentThread();

        private final CallLifecycle.Next next;

        private boolean called;

        private boolean closed;

        private CallOutcome outcome;

        private Continuation(CallLifecycle.Next next) {
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

    /** 异步 Next 的一次性调用控制和最终结果记录。 */
    private static class AsyncContinuation implements CallLifecycle.AsyncNext {
        private final Supplier<CompletionStage<CallOutcome>> next;

        private final AtomicBoolean called = new AtomicBoolean();

        private volatile CallOutcome outcome;

        private AsyncContinuation(Supplier<CompletionStage<CallOutcome>> next) {
            this.next = next;
        }

        @Override
        public @NotNull CompletionStage<CallOutcome> proceedAsync() {
            if (!called.compareAndSet(false, true)) {
                return CompletableFuture.failedFuture(new IllegalStateException("AsyncNext 只能执行一次"));
            }
            CompletionStage<CallOutcome> stage;
            try {
                stage = Objects.requireNonNull(next.get(), "异步 Next 不能返回 null");
            } catch (Throwable failure) {
                stage = CompletableFuture.completedFuture(CallOutcome.failed(failure));
            }
            return stage.whenComplete((value, failure) -> {
                if (failure == null) {
                    outcome = value;
                }
            });
        }
    }
}
