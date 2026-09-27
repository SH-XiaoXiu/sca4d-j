package com.sca4d.core.spring.persistence.mybatis;

import org.apache.ibatis.executor.BatchExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.jetbrains.annotations.Nullable;

/** 把 MyBatis `Executor.update()` 返回的真实影响行数交给当前 Repository 的 `@AffectedRows` 声明。 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class AffectedRowsInterceptor implements Interceptor {

    /**
     * 记录当前 `@AffectedRows` Repository 方法执行的唯一一条 DML。
     *
     * @param invocation MyBatis 执行器调用
     * @return MyBatis 原始返回值
     * @throws Throwable SQL 执行、声明检查或 BATCH 检查报告的失败
     */
    @Override
    public @Nullable Object intercept(Invocation invocation) throws Throwable {
        AffectedRowsInvocation current = AffectedRowsInvocation.currentOrNull();
        if (current != null) {
            current.beginUpdate();
        }

        Object result = invocation.proceed();
        if (result instanceof Integer actual
                && actual == BatchExecutor.BATCH_UPDATE_RETURN_VALUE) {
            throw new IllegalStateException("Sca4d-J 不支持 MyBatis BATCH；请使用一条集合 SQL 完成批量写入");
        }
        if (current == null) {
            return result;
        }
        if (!(result instanceof Integer actual)) {
            throw new IllegalStateException("MyBatis Executor.update() 没有返回整数影响行数");
        }
        current.recordAffectedRows(actual);
        return result;
    }
}
