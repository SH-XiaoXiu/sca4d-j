package com.sca4d.core.spring.transaction;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 把当前事务登记的进程内动作按顺序放到成功提交之后执行。 */
public class TransactionCallbacks {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionCallbacks.class);

    private static final Object RESOURCE_KEY = TransactionCallbacks.class;

    private TransactionCallbacks() {
    }

    /**
     * 在当前真实事务提交后执行动作；回滚时不执行。
     *
     * <p>动作在提交线程执行，不持久化或重试。一个动作失败时仍执行其余动作，最后抛出第一个异常，并把后续异常放入 suppressed。
     * 异常在事务已经提交后沿 commit 调用链抛出，调用方不能据此把该事务当作回滚。
     *
     * @param action 不依赖原事务继续写库的进程内动作
     * @throws IllegalStateException 没有真实事务或事务同步未启用时抛出
     */
    public static void afterCommit(@NotNull Runnable action) {
        Runnable checked = Objects.requireNonNull(action, "事务提交后动作不能为空");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("提交后动作要求当前线程存在已启用同步的真实事务");
        }
        queue().add(checked);
    }

    /**
     * 有真实事务时在提交后执行，否则立即执行。
     *
     * <p>仅供公开契约同时允许事务内、事务外调用的动作使用。有真实事务但同步未启用时拒绝提前执行。
     *
     * @param action 提交后或当前立即执行的动作
     * @throws IllegalStateException 真实事务未启用事务同步时抛出
     */
    public static void afterCommitOrNow(@NotNull Runnable action) {
        Runnable checked = Objects.requireNonNull(action, "事务提交后动作不能为空");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            checked.run();
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("当前真实事务未启用同步，不能提前执行提交后动作");
        }
        queue().add(checked);
    }

    /** 为当前事务取得或创建唯一的回调队列。 */
    private static @NotNull CallbackQueue queue() {
        Object existing = TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        if (existing instanceof CallbackQueue callbacks) {
            return callbacks;
        }
        if (existing != null) {
            throw new IllegalStateException("当前事务已绑定其他提交后动作资源");
        }
        CallbackQueue created = new CallbackQueue();
        TransactionSynchronizationManager.bindResource(RESOURCE_KEY, created);
        try {
            TransactionSynchronizationManager.registerSynchronization(created);
            return created;
        } catch (RuntimeException | Error exception) {
            TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
            throw exception;
        }
    }

    /** 保存一个事务的动作；事务挂起时解绑，以隔离 REQUIRES_NEW 的内层事务。 */
    private static class CallbackQueue implements TransactionSynchronization {

        private final List<Runnable> actions = new ArrayList<>();

        private int nextAction;

        private boolean committed;

        private boolean draining;

        private void add(@NotNull Runnable action) {
            actions.add(action);
            if (committed && !draining) {
                drain();
            }
        }

        @Override
        public void suspend() {
            TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
        }

        @Override
        public void resume() {
            TransactionSynchronizationManager.bindResource(RESOURCE_KEY, this);
        }

        @Override
        public void afterCommit() {
            committed = true;
            drain();
        }

        @Override
        public void afterCompletion(int status) {
            try {
                if (status == STATUS_COMMITTED && nextAction < actions.size()) {
                    committed = true;
                    try {
                        drain();
                    } catch (RuntimeException exception) {
                        // 数据库已经提交且 Spring 不会把 afterCompletion 的异常返回给调用方；
                        // 这里记录失败原因，未执行的动作不再重放，需要根据日志人工补偿。
                        LOG.error(
                                "事务提交后动作失败，数据库已提交 | callbackCount={} completedCount={}",
                                actions.size(),
                                nextAction,
                                exception);
                    }
                }
            } finally {
                if (TransactionSynchronizationManager.getResource(RESOURCE_KEY) == this) {
                    TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
                }
            }
        }

        /** 执行登记时及执行期间新增的动作，保留每个运行时失败。 */
        private void drain() {
            if (draining) {
                return;
            }
            draining = true;
            RuntimeException failure = null;
            try {
                while (nextAction < actions.size()) {
                    Runnable action = actions.get(nextAction++);
                    try {
                        action.run();
                    } catch (RuntimeException exception) {
                        if (failure == null) {
                            failure = exception;
                        } else if (failure != exception) {
                            failure.addSuppressed(exception);
                        }
                    }
                }
            } finally {
                draining = false;
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
