package com.sca4d.plugin.spring;

import com.sca4d.core.api.plugin.PluginLease;
import com.sca4d.core.api.plugin.PluginRuntime;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 固定同步调用代次，并把外层事务中的租用延续到事务完成。 */
final class PluginScopeInvocation {
    /** 执行明确声明的作用域，不改变线程、身份或事务传播。 */
    static Object invoke(PluginRuntime runtime, Invocation invocation) throws Throwable {
        PluginLease lease = runtime.openScope();
        boolean deferred = TransactionSynchronizationManager.isSynchronizationActive();
        // 事务同步已开启时保留租用到提交或回滚完成，避免事务结束前卸载插件实现。
        if (deferred) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    lease.close();
                }
            });
        }
        try {
            return invocation.invoke();
        } finally {
            if (!deferred)
                lease.close();
        }
    }

    /** 表达保留原有异常的同步方法调用。 */
    @FunctionalInterface
    interface Invocation {
        /** 调用原有代理方法。 */
        Object invoke() throws Throwable;
    }
}
