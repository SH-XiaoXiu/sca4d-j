package com.sca4d.plugin.spring;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.PluginRuntime;
import com.sca4d.core.api.plugin.PluginScope;
import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

/** 在统一 Call 生命周期中固定插件代次，并把租用延续到外层事务完成。 */
public final class PluginScopeCallInterceptor
        implements
            CallLifecycle.Around,
            CallLifecycleAdvisor.MethodBinding {

    /** 插件作用域生命周期节点。 */
    public static final TreeCode CODE = TreeCode.of("CALL/PLUGIN_SCOPE");

    private final PluginRuntime runtime;

    /** 创建插件作用域生命周期参与者。 */
    public PluginScopeCallInterceptor(@NotNull PluginRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public boolean matches(@NotNull Method method, @NotNull Class<?> targetType) {
        return AnnotatedElementUtils.hasAnnotation(method, PluginScope.class)
                || AnnotatedElementUtils.hasAnnotation(targetType, PluginScope.class);
    }

    @Override
    public @NotNull Optional<Call> createCall(
            @NotNull String callId,
            @NotNull Instant startedAt,
            @NotNull Method method,
            @NotNull Class<?> targetType) {
        return Optional.of(Call.system(callId, startedAt));
    }

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
