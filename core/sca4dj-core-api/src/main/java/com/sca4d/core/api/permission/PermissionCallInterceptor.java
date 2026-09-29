package com.sca4d.core.api.permission;

import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.NoCallValueResolver;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.resource.ResourceReference;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 解析 `@RequirePermission`，执行权限判断，并登记必须由 Repository 消费的资源范围。 */
public class PermissionCallInterceptor implements CallLifecycle.Around {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionCallInterceptor.class);

    private final PermissionAssessment permissionAssessment;

    private final CallValueResolverLookup resolverLookup;

    private final SPICallValueResolver expressionResolver;

    /**
     * 创建权限调用评估器。
     *
     * @param permissionAssessment 按资源查找唯一策略的权限评估器
     * @param resolverLookup 动态资源解析器查找入口
     */
    public PermissionCallInterceptor(
            @NotNull PermissionAssessment permissionAssessment,
            @NotNull CallValueResolverLookup resolverLookup,
            @NotNull SPICallValueResolver expressionResolver) {
        this.permissionAssessment = Objects.requireNonNull(permissionAssessment, "权限评估器不能为空");
        this.resolverLookup = Objects.requireNonNull(resolverLookup, "调用值解析器目录不能为空");
        this.expressionResolver = Objects.requireNonNull(expressionResolver, "注解表达式解析器不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CallLifecycle.PERMISSION;
    }

    @Override
    public boolean supports(@NotNull CallExecution execution) {
        return !execution.declarations(RequirePermission.class).isEmpty();
    }

    /**
     * 判断当前方法声明的全部权限。
     *
     * @param execution 已完成身份和 Principal 选择的方法执行
     * @param next 通过全部权限后继续的调用
     * @return 内层调用结果
     * @throws BusinessException 任一权限被拒绝时抛出
     * @throws IllegalStateException 权限声明、解析结果或策略配置无效时抛出
     */
    @Override
    public @NotNull CallOutcome around(@NotNull CallExecution execution, @NotNull CallLifecycle.Next next) {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");

        // 解析并去重权限声明，同一 `Permission` 在一次调用中只执行一次策略判断。
        List<Permission> resolvedPermissions = new ArrayList<>();
        Set<Permission> distinctPermissions = new LinkedHashSet<>();
        for (RequirePermission requirement : checked.declarations(RequirePermission.class)) {
            for (Permission permission : resolvePermissions(requirement, checked)) {
                if (distinctPermissions.add(permission)) {
                    resolvedPermissions.add(permission);
                }
            }
        }

        // 复用同一个 `Call` 已有的权限结论，并为携带范围的允许结论登记成功要求。
        for (Permission permission : resolvedPermissions) {
            PermissionDecision decision = checked.call().permissionDecision(permission).orElseGet(() -> {
                PermissionDecision assessed = permissionAssessment.assess(checked.call(), permission);
                checked.call().recordPermissionDecision(permission, assessed);
                return assessed;
            });
            if (!decision.isAllowed()) {
                LOG.trace(
                        "权限判断拒绝 | callId={} principalId={} permission={} reason={}",
                        checked.call().callId(),
                        checked.call().principal().map(Principal::principalId).orElse(null),
                        permission,
                        decision.reason().orElse(null));
                throw new BusinessException(CoreErrorCode.PERMISSION_DENIED);
            }
            if (decision.scope().isPresent()) {
                ResourceScopeRequirement.register(checked, permission);
            }
        }
        return next.proceed();
    }

    /**
     * 将一项权限声明解析为使用同一动作的权限集合。
     *
     * @param requirement 当前权限声明
     * @param execution 当前方法执行
     * @return 非空权限集合
     */
    private @NotNull List<@NotNull Permission> resolvePermissions(
            @NotNull RequirePermission requirement,
            @NotNull CallExecution execution) {
        boolean fixedOperation = !requirement.operation().isBlank();
        boolean expression = !requirement.resource().isBlank();
        boolean dynamicResource = requirement.resolver() != NoCallValueResolver.class;
        if ((fixedOperation ? 1 : 0) + (expression ? 1 : 0) + (dynamicResource ? 1 : 0) != 1) {
            throw new IllegalStateException("权限声明必须在固定操作、资源表达式和资源解析器之间三选一");
        }
        Action action;
        if (requirement.action().isBlank()) {
            if (!fixedOperation) {
                throw new IllegalStateException("动态资源权限必须明确声明动作");
            }
            action = Action.INVOKE;
        } else {
            action = new Action(requirement.action());
        }
        if (fixedOperation) {
            return List.of(new Permission(new Operation(requirement.operation()), action));
        }

        Object resolved = expression
                ? expressionResolver.resolve(requirement.resource(), execution)
                : resolverLookup.resolver(requirement.resolver()).resolve(execution);
        if (expression && !(resolved instanceof Collection<?>)) {
            return List.of(new Permission(ResourceReference.fromValue(resolved, requirement.resourceType()), action));
        }
        if (!(resolved instanceof Collection<?> resources)) {
            throw new IllegalStateException(
                    "调用值解析器返回类型不匹配: "
                            + requirement.resolver().getName()
                            + "，要求 "
                            + Collection.class.getName());
        }
        if (resources.isEmpty()) {
            throw new IllegalStateException(
                    "动态资源解析器不能返回空集合: "
                            + requirement.resolver().getName());
        }
        List<Permission> permissions = new ArrayList<>(resources.size());
        for (Object value : resources) {
            if (expression) {
                permissions.add(new Permission(ResourceReference.fromValue(value, requirement.resourceType()), action));
                continue;
            }
            if (!(value instanceof Resource resource)) {
                throw new IllegalStateException(
                        "动态资源解析器集合元素必须实现 Resource: "
                                + requirement.resolver().getName());
            }
            permissions.add(new Permission(resource, action));
        }
        return permissions;
    }

}
