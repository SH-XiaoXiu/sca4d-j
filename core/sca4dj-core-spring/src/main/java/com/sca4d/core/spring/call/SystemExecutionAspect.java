package com.sca4d.core.spring.call;

import com.sca4d.core.api.annotation.SystemExecution;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.name.code.TreeCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** 在自主入口建立系统 Call，并在方法结束后清理当前线程；不覆盖用户或外部应用调用。 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public final class SystemExecutionAspect {

    private final @NotNull Clock clock;

    /** @param clock 生成系统 Call 开始时间的时钟 */
    public SystemExecutionAspect(@NotNull Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * 使用已发布的系统身份执行入口；已有 Call 时沿用调用方身份，身份未登记时立即失败。
     *
     * @param point 被 `@SystemExecution` 标记的方法调用
     * @param systemExecution 方法声明的系统身份编码
     * @return 被代理方法的返回值；被代理方法抛出的异常原样向上抛出
     * @throws Throwable 被代理方法抛出的异常
     */
    @Around("@annotation(systemExecution)")
    public @Nullable Object invoke(@NotNull ProceedingJoinPoint point, @NotNull SystemExecution systemExecution)
            throws Throwable {
        if (CallHolder.currentOrNull() != null) {
            return point.proceed();
        }
        String value = systemExecution.value();
        TreeCode code = TreeCode.of(value);
        CallHolder.set(Call.system(UUID.randomUUID().toString(), clock.instant(), SystemPrincipals.require(code)));
        try {
            return point.proceed();
        } finally {
            CallHolder.clear();
        }
    }
}
