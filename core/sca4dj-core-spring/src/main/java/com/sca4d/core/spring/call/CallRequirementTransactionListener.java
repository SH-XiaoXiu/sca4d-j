package com.sca4d.core.spring.call;

import com.sca4d.core.api.call.CallExecution;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/** 在 Spring 事务提交前检查当前方法执行登记的权限范围和版本条件等要求。 */
public class CallRequirementTransactionListener implements TransactionExecutionListener {

    /** 创建事务提交前调用要求监听器。 */
    public CallRequirementTransactionListener() {
    }

    /**
     * 在资源真正提交前检查仍处于业务方法中的全部执行。
     *
     * @param transaction 即将提交的 Spring 事务
     */
    @Override
    public void beforeCommit(@NotNull TransactionExecution transaction) {
        if (CallHolder.currentOrNull() == null) {
            return;
        }
        for (CallExecution execution : CallHolder.executions()) {
            execution.verifyRequirementsBeforeCommit(transaction.isReadOnly());
        }
    }
}
