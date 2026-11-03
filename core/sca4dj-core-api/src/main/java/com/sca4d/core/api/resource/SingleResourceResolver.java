package com.sca4d.core.api.resource;

import com.sca4d.core.api.audit.AuditInvocation;
import com.sca4d.core.api.audit.AuditTargetResolver;
import com.sca4d.core.api.call.AbstractCallValueResolver;
import com.sca4d.core.api.call.CallExecution;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * 把一个业务资源同时适配为权限检查的资源集合和审计日志的目标。
 *
 * <p>当一个方法的权限检查和审计指向同一资源时，子类只需实现
 * {@link #resolveResource(CallExecution)}，同一个实现类便可配置到两个注解中。
 * 例如实现返回 {@code Resource.Ref("DOCUMENT", "42")} 时，权限入口得到包含该引用的集合，
 * 审计入口得到该引用本身。需要同时检查多个资源的操作不适用本类。
 *
 * <p>审计发生在业务执行之后。删除操作或失败调用可能已经无法读取实体，
 * 此时子类应覆盖 {@link #resolve(AuditInvocation)}，从参数构造稳定的资源引用，
 * 不要重新查询已删除实体。此类不缓存资源，也不复用权限结论。
 */
public abstract class SingleResourceResolver extends AbstractCallValueResolver<Collection<Resource>>
        implements
            AuditTargetResolver {

    /** 将业务资源包装为权限接口要求的单元素集合；资源不能为空。 */
    @Override
    public final @NotNull Collection<Resource> resolve(@NotNull CallExecution execution) {
        return List.of(resolveResource(execution));
    }

    /** 默认以同一个业务资源为审计目标；删除操作应覆盖此方法以避免再次查询实体。 */
    @Override
    public @NotNull Resource resolve(@NotNull AuditInvocation invocation) {
        return resolveResource(invocation.execution());
    }

    /** 读取并校验注解所指的业务资源；不存在或归属不符时由子类报告业务错误。 */
    protected abstract @NotNull Resource resolveResource(@NotNull CallExecution execution);
}
