package com.sca4d.core.api.call;

import com.sca4d.core.api.annotation.Locked;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;

/** 解析 `@Locked` 的锁键，并在分布式锁内连续执行实际业务方法。 */
public class LockedCallExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(LockedCallExecutor.class);

    private final DistributedLock distributedLock;

    private final CallValueResolverLookup resolverLookup;

    /**
     * 创建分布式锁调用执行器。
     *
     * @param distributedLock 分布式锁实现
     * @param resolverLookup 锁键解析器查找入口
     */
    public LockedCallExecutor(
            DistributedLock distributedLock,
            CallValueResolverLookup resolverLookup) {
        this.distributedLock = Objects.requireNonNull(distributedLock, "分布式锁不能为空");
        this.resolverLookup = Objects.requireNonNull(resolverLookup, "调用值解析器目录不能为空");
    }

    /**
     * 在当前方法声明的分布式锁内执行下一阶段。
     *
     * @param execution 当前方法执行
     * @param invocation 实际业务方法阶段
     * @param <T> 业务返回类型
     * @return 业务方法结果
     * @throws Exception 业务方法抛出的受检异常
     */
    public <T> @Nullable T execute(CallExecution execution, Callable<T> invocation) throws Exception {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        Callable<T> checkedInvocation = Objects.requireNonNull(invocation, "下一调用阶段不能为空");
        Locked locked = checked.locked().orElse(null);
        if (locked == null) {
            return checkedInvocation.call();
        }
        Object resolvedKey = resolverLookup.resolver(locked.keyResolver()).resolve(checked);
        if (!(resolvedKey instanceof LockKey lockKey)) {
            throw new IllegalStateException(
                    "调用值解析器返回类型不匹配: "
                            + locked.keyResolver().getName()
                            + "，要求 "
                            + LockKey.class.getName());
        }
        try {
            return distributedLock.execute(
                    lockKey,
                    Duration.ofMillis(locked.waitMillis()),
                    () -> {
                        LOG.trace(
                                "调用已取得分布式锁 | callId={} method={}",
                                checked.call().callId(),
                                checked.method().getName());
                        try {
                            return checkedInvocation.call();
                        } catch (Exception exception) {
                            throw new InvocationFailure(exception);
                        }
                    });
        } catch (InvocationFailure failure) {
            throw failure.original;
        }
    }

    /** 保存需要穿过 `DistributedLock` 回调边界的受检异常。 */
    private static class InvocationFailure extends RuntimeException {

        private final Exception original;

        /** @param original 业务方法抛出的受检异常 */
        private InvocationFailure(Exception original) {
            super(original);
            this.original = original;
        }

    }
}
