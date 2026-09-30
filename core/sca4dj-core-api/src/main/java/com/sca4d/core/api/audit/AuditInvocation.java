package com.sca4d.core.api.audit;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallOutcome;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Objects;

/** 向 Audit 目标解析器和 payload 工厂暴露一次已经得到结果的方法执行。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class AuditInvocation {

    private final AuditResult auditResult;

    private final CallExecution execution;

    /**
     * 创建审计调用快照。
     *
     * @param execution 已经得到成功、拒绝或失败结果的方法执行
     * @param auditResult 写入审计记录的结果分类
     */
    public AuditInvocation(CallExecution execution, AuditResult auditResult) {
        this.execution = Objects.requireNonNull(execution, "方法执行不能为空");
        this.auditResult = Objects.requireNonNull(auditResult, "审计结果不能为空");
        if (execution.outcome().isEmpty()) {
            throw new IllegalStateException("建立审计调用前必须先写入方法执行结果");
        }
    }

    /** @return 当前传输调用 */
    public Call call() {
        return execution.call();
    }

    /** @return 当前方法执行 */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "审计解析器需要读取完整的 CallExecution；该对象仅在调用生命周期内由框架持有")
    public CallExecution execution() {
        return execution;
    }

    /** @return 被调用方法 */
    public Method method() {
        return execution.method();
    }

    /** @return 方法实参副本 */
    public Object[] arguments() {
        return execution.arguments();
    }

    /** @return 成功或幂等重放的返回值；拒绝和失败时为空 */
    public @Nullable Object result() {
        return execution.outcome().orElseThrow().result();
    }

    /**
     * 按调用方指定的类型返回成功调用的业务结果。
     *
     * @param resultType 业务结果的实际类型
     * @param <T> 业务结果类型
     * @return 经过类型检查的业务结果
     * @throws IllegalStateException 当前调用没有非空成功返回值
     * @throws ClassCastException 返回值不是指定类型
     */
    public <T> @NotNull T getResult(@NotNull Class<T> resultType) {
        Objects.requireNonNull(resultType, "业务结果类型不能为空");
        Object result = result();
        if (auditResult != AuditResult.SUCCESS || result == null) {
            throw new IllegalStateException("当前调用没有成功返回值");
        }
        return resultType.cast(result);
    }

    /** @return 拒绝或失败的原始异常；成功时为空 */
    public @Nullable Throwable error() {
        return execution.outcome().orElseThrow().error();
    }

    /** @return 当前审计结果 */
    public AuditResult auditResult() {
        return auditResult;
    }

    /** @return 当前方法执行的详细结果 */
    public CallOutcome outcome() {
        return execution.outcome().orElseThrow();
    }
}
