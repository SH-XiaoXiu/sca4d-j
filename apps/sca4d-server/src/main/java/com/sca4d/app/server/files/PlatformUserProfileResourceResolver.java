package com.sca4d.app.server.files;

import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.app.server.files.request.CreateFileUploadRequest;
import com.sca4d.app.server.service.PlatformAccountApplicationService;
import com.sca4d.core.api.audit.AuditInvocation;
import com.sca4d.core.api.audit.AuditTargetResolver;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallValueResolver;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 从头像上传请求读取目标 Account，并解析其 Platform 用户资料资源。
 */
@Component
public final class PlatformUserProfileResourceResolver
        implements
            CallValueResolver<Collection<Resource>>,
            AuditTargetResolver {

    private final @NotNull PlatformAccountApplicationService accounts;

    /**
     * 创建读取目标 Platform Account 的用户资料资源解析器。
     *
     * @param accounts Platform Account 应用服务
     */
    public PlatformUserProfileResourceResolver(@NotNull PlatformAccountApplicationService accounts) {
        this.accounts = Objects.requireNonNull(accounts, "Platform Account 应用服务不能为空");
    }

    /**
     * 使用第一个方法实参中的上传请求读取目标用户资料。
     *
     * @param execution 当前头像上传方法执行
     * @return 只包含目标 Platform 用户资料的资源集合
     */
    @Override
    public @NotNull Collection<Resource> resolve(@NotNull CallExecution execution) {
        return List.of(profile(execution.arguments()));
    }

    /**
     * 使用审计调用中的上传请求读取目标用户资料。
     *
     * @param invocation 文件上传 Controller 的完整调用快照
     * @return 目标 Platform 用户资料
     */
    @Override
    public @NotNull Resource resolve(@NotNull AuditInvocation invocation) {
        return profile(invocation.arguments());
    }

    /**
     * 校验 Controller 参数并读取请求指定 Account 的用户资料。
     *
     * @param arguments 文件上传 Controller 实参
     * @return 目标 Platform 用户资料
     */
    private @NotNull PlatformUserProfile profile(@NotNull Object[] arguments) {
        if (arguments.length == 0 || !(arguments[0] instanceof CreateFileUploadRequest request)) {
            throw new IllegalStateException("用户资料资源解析要求第一个实参为文件上传请求");
        }
        return accounts.loadByAccountId(request.getTargetAccountId()).profile();
    }
}
