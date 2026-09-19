package com.sca4d.core.api.call;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 按固定生命周期执行声明解析结果、调用前评估、执行控制、成功检验和结果记录。
 *
 * <p>`CallExecution` 在进入本类前已经完成声明解析。本类不会恢复 Session、选择 Principal 或读取业务资源。
 */
public final class CallLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(CallLifecycle.class);

    private final PermissionCallAssessor permissionAssessor;

    private final RateLimitedCallAssessor rateLimitedAssessor;

    private final IdempotentCallExecutor idempotentExecutor;

    private final LockedCallExecutor lockedExecutor;

    private final AuditCallRecorder auditRecorder;

    /**
     * 创建固定调用生命周期。
     *
     * @param permissionAssessor 权限调用前评估器
     * @param rateLimitedAssessor 限流调用前评估器
     * @param idempotentExecutor 幂等执行控制器
     * @param lockedExecutor 分布式锁执行控制器
     * @param auditRecorder 调用结果记录器
     */
    public CallLifecycle(
            PermissionCallAssessor permissionAssessor,
            RateLimitedCallAssessor rateLimitedAssessor,
            IdempotentCallExecutor idempotentExecutor,
            LockedCallExecutor lockedExecutor,
            AuditCallRecorder auditRecorder) {
        this.permissionAssessor = Objects.requireNonNull(permissionAssessor, "权限调用评估器不能为空");
        this.rateLimitedAssessor = Objects.requireNonNull(rateLimitedAssessor, "限流调用评估器不能为空");
        this.idempotentExecutor = Objects.requireNonNull(idempotentExecutor, "幂等调用执行器不能为空");
        this.lockedExecutor = Objects.requireNonNull(lockedExecutor, "锁调用执行器不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "调用结果记录器不能为空");
    }

    /**
     * 执行一次已经解析声明的方法调用。
     *
     * @param execution 当前方法执行
     * @param invocation 实际业务方法
     * @param <T> 业务返回类型
     * @return 实际业务结果或已保存的幂等结果
     * @throws Exception 业务方法、调用控制或成功检验报告的失败
     */
    public <T> @Nullable T execute(CallExecution execution, Callable<T> invocation) throws Exception {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        Callable<T> checkedInvocation = Objects.requireNonNull(invocation, "业务调用不能为空");

        // 声明解析阶段已经完成；这里检查不同声明组合需要满足的身份前提。
        validateDeclaration(checked);
        boolean recordingOutcome = false;
        try {
            // 调用前评估只读取已经准备好的调用身份、资源和方法实参。
            verifyPrincipal(checked);
            permissionAssessor.assess(checked);
            rateLimitedAssessor.assess(checked);

            // 幂等处理权先于分布式锁取得，锁包围实际业务方法及其内部事务。
            T result = idempotentExecutor.execute(
                    checked,
                    () -> lockedExecutor.execute(checked, () -> invokeAndVerify(checked, checkedInvocation)));
            if (checked.outcome().isEmpty()) {
                checked.succeed(result);
            }

            // 成功或幂等重放结果同步写入审计记录。
            recordingOutcome = true;
            auditRecorder.record(checked);
            LOG.trace(
                    "调用生命周期完成 | callId={} method={} outcome={}",
                    checked.call().callId(),
                    checked.method().getName(),
                    checked.outcome().orElseThrow().status());
            return result;
        } catch (BusinessException exception) {
            if (recordingOutcome) {
                checked.fail(exception);
                LOG.trace(
                        "调用结果记录失败 | callId={} method={}",
                        checked.call().callId(),
                        checked.method().getName(),
                        exception);
                throw exception;
            }
            checked.deny(exception);
            recordFailure(checked, exception);
            LOG.trace(
                    "调用生命周期被拒绝 | callId={} method={} errorCode={}",
                    checked.call().callId(),
                    checked.method().getName(),
                    exception.errorCode().code());
            throw exception;
        } catch (Exception exception) {
            checked.fail(exception);
            if (!recordingOutcome) {
                recordFailure(checked, exception);
            }
            LOG.trace(
                    "调用生命周期失败 | callId={} method={}",
                    checked.call().callId(),
                    checked.method().getName(),
                    exception);
            throw exception;
        }
    }

    /**
     * 检查匿名、权限、审计和认证声明之间的固定约束。
     *
     * @param execution 当前方法执行
     */
    private static void validateDeclaration(CallExecution execution) {
        if (execution.anonymous() && !execution.requiredPermissions().isEmpty()) {
            throw new IllegalStateException("匿名访问不能同时声明权限要求");
        }
        if (execution.anonymous() && execution.audit().isPresent()) {
            throw new IllegalStateException("匿名访问不能声明需要身份的审计记录");
        }
        if (!execution.anonymous() && !execution.call().isAuthenticationAssessed()) {
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    /**
     * 检查权限和审计需要的 Principal 已经由外围选择。
     *
     * @param execution 当前方法执行
     */
    private static void verifyPrincipal(CallExecution execution) {
        boolean principalRequired = !execution.requiredPermissions().isEmpty() || execution.audit().isPresent();
        if (principalRequired && !execution.call().isPrincipalAssessed()) {
            throw new IllegalStateException("当前调用必须在执行前完成业务主体选择");
        }
    }

    /**
     * 执行业务方法，并在其成功结果离开生命周期前检查全部调用要求。
     *
     * @param execution 当前方法执行
     * @param invocation 实际业务方法
     * @param <T> 业务返回类型
     * @return 经过成功检验的业务结果
     * @throws Exception 业务方法或成功检验报告的失败
     */
    private <T> @Nullable T invokeAndVerify(
            CallExecution execution,
            Callable<T> invocation) throws Exception {
        execution.beginInvocation();
        try {
            T result = invocation.call();
            execution.verifyRequirements();
            return result;
        } finally {
            execution.endInvocation();
        }
    }

    /**
     * 记录拒绝或失败结果，记录失败不会替换原始调用异常。
     *
     * @param execution 已写入拒绝或失败结果的方法执行
     * @param original 原始调用异常
     */
    private void recordFailure(CallExecution execution, Exception original) {
        try {
            auditRecorder.record(execution);
        } catch (RuntimeException auditFailure) {
            original.addSuppressed(auditFailure);
        }
    }
}
