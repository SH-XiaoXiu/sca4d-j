package com.sca4d.core.spring.version;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallLifecycleConfiguration;
import com.sca4d.core.api.concurrent.IdempotencyCallInterceptor;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.PermissionCallInterceptor;
import com.sca4d.core.api.version.CompatibilityDecision;
import com.sca4d.core.api.version.CompatibilityStatus;
import com.sca4d.core.api.version.exception.VersionCompatibilityException;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 在 Call 执行前读取 MVC 拦截器写入的版本兼容结果，兼容拒绝时在业务执行前抛出异常。 */
public final class VersionCallInterceptor implements CallLifecycle.Before {
    /** 版本兼容检查在 CallLifecycle 中的编码，供 {@link #order} 引用排序。 */
    public static final TreeCode CODE = TreeCode.of("VERSION:COMPATIBILITY");

    /** @return 版本兼容检查在 CallLifecycle 中的编码 */
    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    /** @return 调用没有 operation 且方法绑定了版本矩阵时返回 true，其余调用不执行版本检查 */
    @Override
    public boolean supports(@NotNull CallExecution execution) {
        return execution.operation().isEmpty()
                && new VersionCallBinding().matches(execution.method(), execution.declaringType());
    }

    /**
     * 读取当前 HTTP 请求属性中的版本结果，结果为不兼容时抛出 {@link VersionCompatibilityException}。
     * 没有 Servlet 请求上下文时直接返回，不做检查。
     */
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

    /**
     * 把版本检查排在权限检查之后、幂等检查之前。
     * 对应参与者未注册时该约束不生效。
     *
     * @param configuration 当前 CallLifecycle 配置
     */
    public static void order(CallLifecycleConfiguration configuration) {
        configuration.afterIfRegistered(CODE, PermissionCallInterceptor.CODE);
        configuration.afterIfRegistered(IdempotencyCallInterceptor.CODE, CODE);
    }
}
