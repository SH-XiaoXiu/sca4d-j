package com.sca4d.core.spring.call;

import com.sca4d.core.api.call.CallExecution;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/** 在 Spring 事务提交前检查当前方法登记的预提交条件。 */
public class CallPreCommitTransactionListener implements TransactionExecutionListener {

    /** 创建事务提交前预提交条件监听器。 */
    public CallPreCommitTransactionListener() {
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
}
