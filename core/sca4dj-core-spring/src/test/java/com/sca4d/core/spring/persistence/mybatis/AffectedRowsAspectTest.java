package com.sca4d.core.spring.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.exception.OptimisticLockException;
import com.sca4d.core.api.concurrent.exception.VersionPreconditionFailedException;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.persistence.exception.AffectedRowsMismatchException;
import org.apache.ibatis.executor.BatchExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/** 验证 AffectedRowsAspect 按 @AffectedRows 声明解释 MyBatis 影响行数，并拒绝 BATCH 和多条 DML 语句。 */
class AffectedRowsAspectTest {

    private static final Method EXECUTOR_UPDATE;

    static {
        try {
            EXECUTOR_UPDATE = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        } catch (NoSuchMethodException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private final AffectedRowsAspect aspect = new AffectedRowsAspect();

    private final AffectedRowsInterceptor interceptor = new AffectedRowsInterceptor();

    @Test
    @DisplayName("Interprets Zero One And Multiple Affected Rows")
    void interpretsZeroOneAndMultipleAffectedRows() throws Throwable {
        assertThat(invoke("required", 1)).isEqualTo(1);
        assertThat(invoke("optional", 0)).isEqualTo(0);

        assertThatThrownBy(() -> invoke("required", 0))
                .isInstanceOf(AffectedRowsMismatchException.class);
        assertThatThrownBy(() -> invoke("required", 2))
                .isInstanceOf(AffectedRowsMismatchException.class);
        assertThatThrownBy(() -> invoke("optimistic", 0))
                .isInstanceOf(OptimisticLockException.class);
        assertThatThrownBy(() -> invoke("versionPrecondition", 0))
                .isInstanceOf(VersionPreconditionFailedException.class);
    }

    @Test
    @DisplayName("Rejects Missing And Multiple Dml Statements")
    void rejectsMissingAndMultipleDmlStatements() throws Throwable {
        ProceedingJoinPoint missing = joinPoint("required");
        when(missing.proceed()).thenReturn(null);

        assertThatThrownBy(() -> aspect.interpret(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须执行一条 DML");

        ProceedingJoinPoint multiple = joinPoint("required");
        Executor executor = executorReturning(1);
        when(multiple.proceed()).thenAnswer(ignored -> {
            interceptor.intercept(updateInvocation(executor));
            return interceptor.intercept(updateInvocation(executor));
        });

        assertThatThrownBy(() -> aspect.interpret(multiple))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只能执行一条 DML");
    }

    @Test
    @DisplayName("Rejects Batch Sentinel And Clears Invocation")
    void rejectsBatchSentinelAndClearsInvocation() throws Throwable {
        assertThatThrownBy(() -> invoke("required", BatchExecutor.BATCH_UPDATE_RETURN_VALUE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不支持 MyBatis BATCH");
        assertThat(AffectedRowsInvocation.currentOrNull()).isNull();

        ProceedingJoinPoint failed = joinPoint("required");
        when(failed.proceed()).thenThrow(new IllegalArgumentException("repository failed"));

        assertThatThrownBy(() -> aspect.interpret(failed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("repository failed");
        assertThat(AffectedRowsInvocation.currentOrNull()).isNull();
    }

    @Test
    @DisplayName("Rejects Batch Without Affected Rows Declaration")
    void rejectsBatchWithoutAffectedRowsDeclaration() throws Throwable {
        Invocation invocation = updateInvocation(executorReturning(BatchExecutor.BATCH_UPDATE_RETURN_VALUE));

        assertThatThrownBy(() -> interceptor.intercept(invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不支持 MyBatis BATCH");
    }

    /** 执行一个声明方法，并让 MyBatis 插件返回给定影响行数。 */
    private Object invoke(String methodName, int affectedRows) throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint(methodName);
        Invocation invocation = updateInvocation(executorReturning(affectedRows));
        when(joinPoint.proceed()).thenAnswer(ignored -> interceptor.intercept(invocation));
        return aspect.interpret(joinPoint);
    }

    /** 创建指向指定声明方法的 Spring JoinPoint。 */
    private static ProceedingJoinPoint joinPoint(String methodName) throws NoSuchMethodException {
        Method method = DeclaredRepository.class.getDeclaredMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn(DeclaredRepository.class);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new DeclaredRepository());
        return joinPoint;
    }

    /** 创建返回固定影响行数的 MyBatis 执行器。 */
    private static Executor executorReturning(int affectedRows) throws Exception {
        Executor executor = mock(Executor.class);
        when(executor.update(null, null)).thenReturn(affectedRows);
        return executor;
    }

    /** 创建一次 MyBatis Executor.update 调用。 */
    private static Invocation updateInvocation(Executor executor) {
        return new Invocation(executor, EXECUTOR_UPDATE, new Object[] {null, null});
    }

    /** 只用于验证 Repository 影响行数声明。 */
    private static class DeclaredRepository {

        /** 必须影响一行。 */
        @AffectedRows(target = "测试设置")
        void required() {
        }

        /** 允许没有发生修改。 */
        @AffectedRows(target = "测试设置", whenZero = ZeroRowPolicy.ALLOW)
        void optional() {
        }

        /** 零行表示内部版本冲突。 */
        @AffectedRows(target = "测试设置", whenZero = ZeroRowPolicy.OPTIMISTIC_LOCK)
        void optimistic() {
        }

        /** 零行表示调用方版本前置条件失败。 */
        @AffectedRows(target = "测试设置", whenZero = ZeroRowPolicy.VERSION_PRECONDITION)
        void versionPrecondition() {
        }
    }
}
