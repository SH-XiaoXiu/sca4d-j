package com.sca4d.openapi.web;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.IdentityCallInterceptor;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.PermissionCallInterceptor;
import com.sca4d.open.client.OpenClientOperationChecker;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

/** 在 Call 生命周期中检查开放路由的 APPLICATION Client 身份和 Operation 权限。 */
public final class OpenApiOperationCallInterceptor implements CallLifecycle.Before {

    /** 开放 API Operation 检查节点编码。 */
    public static final TreeCode CODE = TreeCode.of("CALL/OPEN_API_OPERATION");

    private static final Logger LOG = LoggerFactory.getLogger(OpenApiOperationCallInterceptor.class);

    private final @NotNull OpenApiRouteCatalog routes;

    private final @NotNull OpenClientOperationChecker checker;

    /** 保存开放路由目录和 Client Operation 检查器。 */
    public OpenApiOperationCallInterceptor(
            @NotNull OpenApiRouteCatalog routes,
            @NotNull OpenClientOperationChecker checker) {
        this.routes = Objects.requireNonNull(routes, "OpenAPI 路由目录不能为空");
        this.checker = Objects.requireNonNull(checker, "Open Client Operation 检查器不能为空");
    }

    /** 只匹配已经登记的开放 API Controller 方法。 */
    @Override
    public boolean supports(@NotNull CallExecution execution) {
        return routes.find(execution.declaringType(), execution.method()).isPresent();
    }

    /** 检查认证结果和 Client Operation 权限；检查失败时停止 Controller 执行。 */
    @Override
    public void before(@NotNull CallExecution execution) {
        OpenApiRouteDefinition route = routes.find(execution.declaringType(), execution.method())
                .orElseThrow(() -> new IllegalStateException("开放 API CallExecution 缺少路由定义"));
        if (!execution.call().isAuthenticationAssessed()
                || !execution.call()
                        .subject()
                        .map(subject -> subject.type() == SubjectType.APPLICATION)
                        .orElse(false)) {
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        checker.verifyAllowed(execution.call(), route.operation());
        LOG.trace(
                "开放 API Operation 已允许 | callId={} operation={} method={}",
                execution.call().callId(),
                route.operation(),
                execution.method().toGenericString());
    }

    /** @return 开放 API Operation 检查节点编码 */
    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    /** @return 必须在身份检查之后执行 */
    @Override
    public @NotNull Set<TreeCode> after() {
        return Set.of(IdentityCallInterceptor.CODE);
    }

    /** @return 必须在业务 Principal 前置检查和业务权限检查之前执行 */
    @Override
    public @NotNull Set<TreeCode> before() {
        return Set.of(
                IdentityCallInterceptor.PrincipalRequirement.CODE,
                PermissionCallInterceptor.CODE);
    }
}
