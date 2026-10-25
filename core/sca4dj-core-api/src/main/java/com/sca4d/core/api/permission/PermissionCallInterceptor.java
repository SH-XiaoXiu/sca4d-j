package com.sca4d.core.api.permission;

import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.NoCallValueResolver;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.exception.CoreApiException;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** 解析 `@RequirePermission`，执行权限判断，并登记必须由 Repository 消费的资源范围。 */
public class PermissionCallInterceptor implements CallLifecycle.Around {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionCallInterceptor.class);

    /** 权限判断节点编码。 */
    public static final TreeCode CODE = TreeCode.of("CALL/PERMISSION");

    private final CallValueResolverLookup resolverLookup;

    private final PermissionAssessment permissionAssessment;

    private final SPICallValueResolver expressionResolver;

    /**
     * 创建权限调用评估器。
     *
     * @param permissionAssessment 按资源查找唯一策略的权限评估器
     * @param resolverLookup 动态资源解析器查找入口
     * @param expressionResolver 解析 {@code @RequirePermission} 资源表达式的解析器
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
        return CODE;
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
        if (!checked.call().isPrincipalAssessed()) {
            throw new IllegalStateException("权限判断前必须完成业务主体选择");
        }

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
     * 异步执行前先完成全部权限判断，只有全部允许后才调用 {@code next.proceedAsync()}。
     *
     * @param execution 已完成身份和 Principal 选择的方法执行
     * @param next 通过全部权限后启动的异步内层调用
     * @return 内层异步调用的完成阶段
     * @throws BusinessException 任一权限被拒绝时同步抛出，不启动内层调用
     * @throws IllegalStateException 尚未选择 Principal 或权限声明无效时同步抛出
     */
    @Override
    public @NotNull CompletionStage<CallOutcome> aroundAsync(
            @NotNull CallExecution execution,
            @NotNull CallLifecycle.AsyncNext next) {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        if (!checked.call().isPrincipalAssessed()) {
            throw new IllegalStateException("权限判断前必须完成业务主体选择");
        }
        List<Permission> resolvedPermissions = new ArrayList<>();
        Set<Permission> distinctPermissions = new LinkedHashSet<>();
        for (RequirePermission requirement : checked.declarations(RequirePermission.class)) {
            for (Permission permission : resolvePermissions(requirement, checked)) {
                if (distinctPermissions.add(permission)) {
                    resolvedPermissions.add(permission);
                }
            }
        }
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
                ResourceScopeRequirement.register(checked, permission);
            }
        }
        return next.proceedAsync();
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
            return List.of(new Permission(reference(resolved, requirement.resourceType()), action));
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
                permissions.add(new Permission(reference(value, requirement.resourceType()), action));
                continue;
            }
            if (!(value instanceof Resource resource)) {
                throw new IllegalStateException(
                        "动态资源表达式或解析器必须返回 Resource: "
                                + requirement.resolver().getName());
            }
            permissions.add(new Permission(resource, action));
        }
        return permissions;
    }

    /**
     * 把权限表达式的返回值转换为 Permission 使用的 Resource。
     *
     * <p>{@link Resource} 必须与 {@code resourceType} 的类型一致；正整数或非空文本只作为标识，并与 {@code resourceType} 组成 {@link Resource.Ref}。
     *
     * @param value 表达式或解析器返回的单个资源值
     * @param resourceType 注解声明的资源类型，可以为空白，但标识值要求它非空
     * @return 可以交给权限策略判断的资源
     * @throws IllegalStateException 资源类型不一致、标识不是正整数或非空文本，或缺少必需的资源类型时抛出
     * @throws CoreApiException 数字标识无法精确转换为整数时抛出
     */
    private static @NotNull Resource reference(@NotNull Object value, @NotNull String resourceType) {
        if (value instanceof Resource resource) {
            if (!resourceType.isBlank() && !TreeCode.of(resourceType).equals(resource.type())) {
                throw new IllegalStateException("资源值与声明的 resourceType 不一致");
            }
            return resource;
        }
        if (resourceType.isBlank()) {
            throw new IllegalStateException("权限资源标识必须声明 resourceType");
        }
        String id;
        if (value instanceof Number number) {
            try {
                BigInteger integer = new BigDecimal(number.toString()).toBigIntegerExact();
                if (integer.signum() <= 0) {
                    throw new IllegalStateException("权限资源标识必须是正整数");
                }
                id = integer.toString();
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new CoreApiException("权限资源标识必须是正整数", exception);
            }
        } else if (value instanceof String text && !text.isBlank()) {
            id = text.trim();
        } else {
            throw new IllegalStateException("权限资源必须是 Resource、正整数或非空文本标识");
        }
        return new Resource.Ref(resourceType, id);
    }

}
