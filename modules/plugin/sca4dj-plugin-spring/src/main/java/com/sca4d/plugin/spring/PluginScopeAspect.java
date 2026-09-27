package com.sca4d.plugin.spring;

import com.sca4d.core.api.plugin.PluginRuntime;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/** 在 App 声明的同步操作外建立插件代次范围，覆盖内部事务提交与回滚。 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 300)
public final class PluginScopeAspect {
    private final PluginRuntime runtime;
    public PluginScopeAspect(PluginRuntime runtime) { this.runtime = runtime; }

    /** 固定一次 App 操作的代次，多次生成客户端调用共享同一快照。 */
    @Around("@annotation(com.sca4d.core.api.plugin.PluginScope)"
            + " || @within(com.sca4d.core.api.plugin.PluginScope)")
    public Object invoke(ProceedingJoinPoint call) throws Throwable {
        return PluginScopeInvocation.invoke(runtime, call::proceed);
    }
}
