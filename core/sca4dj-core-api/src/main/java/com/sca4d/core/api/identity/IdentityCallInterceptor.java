package com.sca4d.core.api.identity;

import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.Idempotent;
import com.sca4d.core.api.annotation.Locked;
import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.annotation.SystemExecution;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

/** 检查调用声明的认证前提，并在主体选择后确认权限和身份审计所需的 Principal。 */
public final class IdentityCallInterceptor implements CallLifecycle.Around {
    private static final Logger LOG = LoggerFactory.getLogger(IdentityCallInterceptor.class);

    /** 身份声明检查节点编码。 */
    public static final TreeCode CODE = TreeCode.of("CALL/IDENTITY");

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public @NotNull CallOutcome around(@NotNull CallExecution execution, @NotNull CallLifecycle.Next next) {
        // 先检查声明和认证，Principal 要求留到选择器执行后再校验。
        boolean anonymous = execution.declaration(AnonymousAccess.class).isPresent();
        boolean principalRequired = !execution.declarations(RequirePermission.class).isEmpty()
                || execution.declaration(Audit.class).isPresent();
        if (anonymous && principalRequired) {
            throw new IllegalStateException("匿名访问不能同时声明权限或身份审计");
        }
        boolean onlySystemEntry = execution.declaration(SystemExecution.class).isPresent()
                && !principalRequired
                && execution.declaration(RateLimited.class).isEmpty()
                && execution.declaration(Idempotent.class).isEmpty()
                && execution.declaration(Locked.class).isEmpty();
        if (!anonymous && !onlySystemEntry && !execution.call().isAuthenticationAssessed()) {
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (principalRequired) {
            execution.requirePrincipal();
        }
        LOG.trace(
                "调用身份前提已检查 | callId={} authenticated={} principalRequired={}",
                execution.call().callId(),
                execution.call().isAuthenticationAssessed(),
                principalRequired);
        return next.proceed();
    }

    /** 异步业务完成只延后内层阶段；身份前提仍在进入业务方法前同步完成。 */
    @Override
    public @NotNull CompletionStage<CallOutcome> aroundAsync(
            @NotNull CallExecution execution,
            @NotNull CallLifecycle.AsyncNext next) {
        boolean anonymous = execution.declaration(AnonymousAccess.class).isPresent();
        boolean principalRequired = !execution.declarations(RequirePermission.class).isEmpty()
                || execution.declaration(Audit.class).isPresent();
        if (anonymous && principalRequired) {
            throw new IllegalStateException("匿名访问不能同时声明权限或身份审计");
        }
        boolean onlySystemEntry = execution.declaration(SystemExecution.class).isPresent()
                && !principalRequired
                && execution.declaration(RateLimited.class).isEmpty()
                && execution.declaration(Idempotent.class).isEmpty()
                && execution.declaration(Locked.class).isEmpty();
        if (!anonymous && !onlySystemEntry && !execution.call().isAuthenticationAssessed()) {
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (principalRequired) {
            execution.requirePrincipal();
        }
        return next.proceedAsync();
    }
}
