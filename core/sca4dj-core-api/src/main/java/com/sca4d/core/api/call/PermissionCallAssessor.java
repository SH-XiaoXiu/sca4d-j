package com.sca4d.core.api.call;

import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionAssessment;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.resource.Resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 解析 `@RequirePermission`，执行权限判断，并登记必须由 Repository 消费的资源范围。 */
public final class PermissionCallAssessor {

    private final PermissionAssessment permissionAssessment;

    private final CallValueResolverLookup resolverLookup;

    /**
     * 创建权限调用评估器。
     *
     * @param permissionAssessment 按资源查找唯一策略的权限评估器
     * @param resolverLookup 动态资源解析器查找入口
     */
    public PermissionCallAssessor(
            PermissionAssessment permissionAssessment,
            CallValueResolverLookup resolverLookup) {
        this.permissionAssessment = Objects.requireNonNull(permissionAssessment, "权限评估器不能为空");
        this.resolverLookup = Objects.requireNonNull(resolverLookup, "调用值解析器目录不能为空");
    }

    /**
     * 判断当前方法声明的全部权限。
     *
     * @param execution 已完成身份和 Principal 选择的方法执行
     * @throws BusinessException 任一权限被拒绝时抛出
     * @throws IllegalStateException 权限声明、解析结果或策略配置无效时抛出
     */
    public void assess(CallExecution execution) {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        if (checked.requiredPermissions().isEmpty()) {
            return;
        }

        // 解析并去重权限声明，同一 `Permission` 在一次调用中只执行一次策略判断。
        List<Permission> resolvedPermissions = new ArrayList<>();
        Set<Permission> distinctPermissions = new LinkedHashSet<>();
        for (RequirePermission requirement : checked.requiredPermissions()) {
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
                throw new BusinessException(CoreErrorCode.PERMISSION_DENIED);
            }
            if (decision.scope().isPresent()) {
                checked.registerResourceScope(permission);
            }
        }
    }

    /**
     * 将一项权限声明解析为使用同一动作的权限集合。
     *
     * @param requirement 当前权限声明
     * @param execution 当前方法执行
     * @return 非空权限集合
     */
    private List<Permission> resolvePermissions(
            RequirePermission requirement,
            CallExecution execution) {
        boolean fixedOperation = !requirement.operation().isBlank();
        boolean dynamicResource = requirement.resourceResolver() != NoCallValueResolver.class;
        if (fixedOperation == dynamicResource) {
            throw new IllegalStateException("权限声明必须在固定操作和动态资源之间二选一");
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

        Object resolved = resolverLookup.resolver(requirement.resourceResolver()).resolve(execution);
        if (!(resolved instanceof Collection<?> resources)) {
            throw new IllegalStateException(
                    "调用值解析器返回类型不匹配: "
                            + requirement.resourceResolver().getName()
                            + "，要求 "
                            + Collection.class.getName());
        }
        if (resources.isEmpty()) {
            throw new IllegalStateException(
                    "动态资源解析器不能返回空集合: "
                            + requirement.resourceResolver().getName());
        }
        List<Permission> permissions = new ArrayList<>(resources.size());
        for (Object value : resources) {
            if (!(value instanceof Resource resource)) {
                throw new IllegalStateException(
                        "动态资源解析器集合元素必须实现 Resource: "
                                + requirement.resourceResolver().getName());
            }
            permissions.add(new Permission(resource, action));
        }
        return permissions;
    }

}
