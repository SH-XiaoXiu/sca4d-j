package com.sca4d.core.api.permission;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallRequirement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/** 保存一次方法执行必须由 Repository 消费的权限资源范围。 */
public final class ResourceScopeRequirement implements CallRequirement {
    private final Permission permission;

    private final ResourceScope scope;

    private boolean consumed;

    private ResourceScopeRequirement(Permission permission, ResourceScope scope) {
        this.permission = permission;
        this.scope = scope;
    }

    /** 登记当前 Call 已批准的范围；同一方法执行中的同一 Permission 只登记一次。 */
    @Contract(mutates = "param1")
    public static void register(@NotNull CallExecution execution, @NotNull Permission permission) {
        Objects.requireNonNull(execution);
        Objects.requireNonNull(permission);
        PermissionDecision decision = execution.call()
                .permissionDecision(permission)
                .filter(PermissionDecision::isAllowed)
                .filter(item -> item.scope().isPresent())
                .orElseThrow(() -> new IllegalStateException("当前权限没有可登记的资源范围: " + permission));
        if (execution.requirements(ResourceScopeRequirement.class)
                .stream()
                .noneMatch(requirement -> requirement.permission.equals(permission))) {
            execution.addRequirement(new ResourceScopeRequirement(permission, decision.scope().orElseThrow()));
        }
    }

    /** 消费指定执行中的范围；没有登记时返回空，范围类型不符时拒绝消费。 */
    @Contract(mutates = "param1")
    public static <S extends ResourceScope> @NotNull Optional<S> consume(
            @NotNull CallExecution execution,
            @NotNull Permission permission,
            @NotNull Class<S> scopeType) {
        Objects.requireNonNull(execution);
        Objects.requireNonNull(permission);
        Objects.requireNonNull(scopeType);
        for (ResourceScopeRequirement requirement : execution.requirements(ResourceScopeRequirement.class)) {
            if (requirement.permission.equals(permission)) {
                if (!scopeType.isInstance(requirement.scope)) {
                    throw new IllegalStateException(
                            "资源范围类型不匹配: 实际 " + requirement.scope.getClass().getName()
                                    + "，要求 " + scopeType.getName());
                }
                requirement.consumed = true;
                return Optional.of(scopeType.cast(requirement.scope));
            }
        }
        return Optional.empty();
    }

    @Override
    public void verifySatisfied() {
        if (!consumed) {
            throw new IllegalStateException("业务方法声明的数据权限范围没有被 Repository 消费: " + permission);
        }
    }
}
