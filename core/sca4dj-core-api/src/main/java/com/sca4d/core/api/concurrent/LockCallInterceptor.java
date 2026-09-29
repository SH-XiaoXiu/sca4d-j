package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.annotation.Locked;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.NoCallValueResolver;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/** 解析 `@Locked` 的锁键，并在分布式锁内连续执行实际业务方法。 */
public class LockCallInterceptor implements CallLifecycle.Around {

    private static final Logger LOG = LoggerFactory.getLogger(LockCallInterceptor.class);

    /** 分布式锁节点编码。 */
    public static final TreeCode CODE = TreeCode.of("CALL/LOCK");

    private final DistributedLock distributedLock;

    private final CallValueResolverLookup resolverLookup;

    private final SPICallValueResolver expressionResolver;

    /**
     * 创建分布式锁调用执行器。
     *
     * @param distributedLock 分布式锁实现
     * @param resolverLookup 锁键解析器查找入口
     */
    public LockCallInterceptor(
            @NotNull DistributedLock distributedLock,
            @NotNull CallValueResolverLookup resolverLookup,
            @NotNull SPICallValueResolver expressionResolver) {
        this.distributedLock = Objects.requireNonNull(distributedLock, "分布式锁不能为空");
        this.resolverLookup = Objects.requireNonNull(resolverLookup, "调用值解析器目录不能为空");
        this.expressionResolver = Objects.requireNonNull(expressionResolver, "注解表达式解析器不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public boolean supports(@NotNull CallExecution execution) {
        return execution.declaration(Locked.class).isPresent();
    }

    @Override
    public @NotNull CallOutcome around(@NotNull CallExecution execution, @NotNull CallLifecycle.Next next) {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        Locked locked = checked.declaration(Locked.class).orElseThrow();
        boolean expression = !locked.key().isBlank();
        boolean customResolver = locked.keyResolver() != NoCallValueResolver.class;
        if (expression == customResolver) {
            throw new IllegalStateException("锁 key 与 keyResolver 必须二选一");
        }
        Object resolvedKey = expression
                ? expressionResolver.resolve(locked.key(), checked)
                : resolverLookup.resolver(locked.keyResolver()).resolve(checked);
        if (!(resolvedKey instanceof LockKey lockKey)) {
            throw new IllegalStateException(
                    "调用值解析器返回类型不匹配: "
                            + locked.keyResolver().getName()
                            + "，要求 "
                            + LockKey.class.getName());
        }
        AtomicReference<CallOutcome> completed = new AtomicReference<>();
        try {
            return distributedLock.execute(
                    lockKey,
                    Duration.ofMillis(locked.waitMillis()),
                    () -> {
                        LOG.trace(
                                "调用已取得分布式锁 | callId={} method={}",
                                checked.call().callId(),
                                checked.invocationName());
                        CallOutcome outcome = next.proceed();
                        completed.set(outcome);
                        return outcome;
                    });
        } catch (RuntimeException | Error lockFailure) {
            CallOutcome outcome = completed.get();
            if (outcome == null) {
                throw lockFailure;
            }
            return outcome.withCompletionFailure(lockFailure);
        }
    }

    /** 异步任务完成并释放分布式锁后才继续外层生命周期。 */
    @Override
    public @NotNull CompletionStage<CallOutcome> aroundAsync(
            @NotNull CallExecution execution,
            @NotNull CallLifecycle.AsyncNext next) {
        Locked locked = execution.declaration(Locked.class).orElseThrow();
        boolean expression = !locked.key().isBlank();
        boolean customResolver = locked.keyResolver() != NoCallValueResolver.class;
        if (expression == customResolver) {
            throw new IllegalStateException("锁 key 与 keyResolver 必须二选一");
        }
        Object resolvedKey = expression
                ? expressionResolver.resolve(locked.key(), execution)
                : resolverLookup.resolver(locked.keyResolver()).resolve(execution);
        if (!(resolvedKey instanceof LockKey lockKey)) {
            throw new IllegalStateException("异步锁键解析结果必须是 LockKey");
        }
        AtomicReference<CallOutcome> completed = new AtomicReference<>();
        return distributedLock.executeAsync(
                lockKey,
                Duration.ofMillis(locked.waitMillis()),
                () -> next.proceedAsync().thenApply(outcome -> {
                    completed.set(outcome);
                    return outcome;
                }))
                .handle((outcome, failure) -> {
                    if (failure == null) {
                        return outcome;
                    }
                    Throwable cause = failure instanceof java.util.concurrent.CompletionException
                            && failure.getCause() != null ? failure.getCause() : failure;
                    CallOutcome business = completed.get();
                    if (business != null) {
                        return business.withCompletionFailure(cause);
                    }
                    throw new java.util.concurrent.CompletionException(cause);
                });
    }
}
