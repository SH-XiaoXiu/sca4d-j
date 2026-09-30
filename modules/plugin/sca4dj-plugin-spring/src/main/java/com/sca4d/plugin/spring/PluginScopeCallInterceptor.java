package com.sca4d.plugin.spring;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.PluginRuntime;
import com.sca4d.core.api.plugin.PluginScope;
import org.jetbrains.annotations.NotNull;

/** 在统一 Call 生命周期中固定插件代次，并把租用延续到外层事务完成。 */
public final class PluginScopeCallInterceptor implements CallLifecycle.Around {

    /** 插件作用域生命周期节点。 */
    public static final TreeCode CODE = TreeCode.of("CALL/PLUGIN_SCOPE");

    private final PluginRuntime runtime;

    public PluginScopeCallInterceptor(@NotNull PluginRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public boolean supports(@NotNull CallExecution execution) {
        return execution.declaration(PluginScope.class).isPresent();
    }

    /** 在插件声明的方法中固定一个调用代次。 */
    @Override
    public @NotNull CallOutcome around(
            @NotNull CallExecution execution,
            @NotNull CallLifecycle.Next next) {
        try {
            return (CallOutcome) PluginScopeInvocation.invoke(runtime, next::proceed);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("插件作用域调用失败", failure);
        }
    }
}
