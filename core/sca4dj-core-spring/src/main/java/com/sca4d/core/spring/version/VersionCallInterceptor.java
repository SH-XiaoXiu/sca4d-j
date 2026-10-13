package com.sca4d.core.spring.version;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallLifecycleConfiguration;
import com.sca4d.core.api.concurrent.IdempotencyCallInterceptor;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.PermissionCallInterceptor;
import com.sca4d.core.api.version.CompatibilityDecision;
import com.sca4d.core.api.version.CompatibilityStatus;
import com.sca4d.core.api.version.VersionCompatibilityException;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 在 Call 中报告兼容拒绝，使审计、拒绝分类和完成处理保持统一。 */
public final class VersionCallInterceptor implements CallLifecycle.Before {
    /** 版本检查的稳定生命周期编码。 */
    public static final TreeCode CODE = TreeCode.of("VERSION:COMPATIBILITY");

    /** @return 生命周期编码 */
    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    /** @return HTTP 入口以外的调用不受客户端版本约束 */
    @Override
    public boolean supports(@NotNull CallExecution execution) {
        return execution.operation().isEmpty()
                && new VersionCallBinding().matches(execution.method(), execution.declaringType());
    }

    /** 在业务、幂等重放和事务执行前拒绝不兼容请求。 */
    @Override
    public void before(@NotNull CallExecution execution) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))
            return;
        var request = attributes.getRequest();
        var invalid = request.getAttribute(VersionCompatibilityException.class.getName());
        if (invalid instanceof VersionCompatibilityException failure)
            throw failure;
        var value = request.getAttribute(VersionWebConfiguration.DECISION);
        if (value instanceof CompatibilityDecision decision && decision.status() == CompatibilityStatus.BLOCKED)
            throw new VersionCompatibilityException(decision);
    }

    /** 在权限之后、幂等重放之前检查，保留 Audit 的最外层观察。 */
    public static void order(CallLifecycleConfiguration configuration) {
        configuration.afterIfRegistered(CODE, PermissionCallInterceptor.CODE);
        configuration.afterIfRegistered(IdempotencyCallInterceptor.CODE, CODE);
    }
}
