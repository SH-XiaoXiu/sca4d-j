package com.sca4d.core.spring.call;

import com.sca4d.core.api.call.CallExecution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 在 Spring 事务提交前检查当前方法登记的预提交条件。 */
public class CallPreCommitTransactionListener implements TransactionExecutionListener {

    public CallPreCommitTransactionListener() {
    }

    /** 将执行关联到当前 Spring 事务，方法先于外层事务结束时仍能检查条件。 */
    public static void track(@NotNull CallExecution execution) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof CallCommitSynchronization tracked && tracked.execution == execution) {
                return;
            }
        }
        TransactionSynchronizationManager.registerSynchronization(new CallCommitSynchronization(execution));
    }

    /** 方法内部开启事务时，在事务同步初始化后关联所有活动方法执行。 */
    @Override
    public void afterBegin(
            @NotNull TransactionExecution transaction,
            @Nullable Throwable failure) {
        if (failure != null || CallHolder.currentOrNull() == null) {
            return;
        }
        CallHolder.executions().forEach(CallPreCommitTransactionListener::track);
    }

    /** 在资源真正提交前检查仍处于业务方法中的全部执行。 */
    @Override
    public void beforeCommit(@NotNull TransactionExecution transaction) {
        if (CallHolder.currentOrNull() == null) {
            return;
        }
        for (CallExecution execution : CallHolder.executions()) {
            execution.verifyPreCommitBeforeCommit(transaction.isReadOnly());
        }
    }

    /** 保存事务期间执行过的方法，事务回调不依赖提交时的线程绑定。 */
    private static class CallCommitSynchronization implements TransactionSynchronization {
        private final CallExecution execution;

        private CallCommitSynchronization(CallExecution execution) {
            this.execution = execution;
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            if (!execution.isInvoking()) {
                execution.verifyPreCommitBeforeCommit(readOnly);
            }
        }

        @Override
        public void afterCommit() {
            execution.afterCommit();
        }
    }
}
