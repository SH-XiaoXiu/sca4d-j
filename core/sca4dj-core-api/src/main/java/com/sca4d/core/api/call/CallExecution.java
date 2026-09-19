package com.sca4d.core.api.call;

import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.Idempotent;
import com.sca4d.core.api.annotation.Locked;
import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.permission.ResourceScope;
import com.sca4d.core.api.utils.AnnotationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 保存一次被声明式调用规则包围的方法执行及其成功要求。 */
public final class CallExecution {

    private final Call call;

    private final Class<?> declaringType;

    private final Method method;

    private final Object[] arguments;

    private final Type resultType;

    private final boolean anonymous;

    private final List<RequirePermission> requiredPermissions;

    private final @Nullable Audit audit;

    private final @Nullable Idempotent idempotent;

    private final @Nullable Locked locked;

    private final @Nullable RateLimited rateLimited;

    private final List<CallRequirement> requirements = new ArrayList<>();

    private final Map<Permission, ResourceScopeRequirement> resourceScopes = new LinkedHashMap<>();

    private @Nullable CallOutcome outcome;

    private boolean invoking;

    /**
     * 创建一次方法执行，并立即解析类型和方法上的调用声明。
     *
     * @param call 当前传输调用
     * @param declaringType 声明业务方法的具体类型
     * @param method 被调用方法
     * @param arguments 方法实参
     * @param resultType 业务返回类型
     */
    public CallExecution(
            Call call,
            Class<?> declaringType,
            Method method,
            Object[] arguments,
            Type resultType) {
        this.call = Objects.requireNonNull(call, "调用不能为空");
        this.declaringType = Objects.requireNonNull(declaringType, "业务类型不能为空");
        this.method = Objects.requireNonNull(method, "业务方法不能为空");
        this.arguments = Arrays.copyOf(Objects.requireNonNull(arguments, "业务方法实参不能为空"), arguments.length);
        this.resultType = Objects.requireNonNull(resultType, "业务返回类型不能为空");
        this.anonymous = declaringType.isAnnotationPresent(com.sca4d.core.api.annotation.AnonymousAccess.class)
                || method.isAnnotationPresent(com.sca4d.core.api.annotation.AnonymousAccess.class);
        List<RequirePermission> permissions = new ArrayList<>();
        permissions.addAll(Arrays.asList(declaringType.getAnnotationsByType(RequirePermission.class)));
        permissions.addAll(Arrays.asList(method.getAnnotationsByType(RequirePermission.class)));
        this.requiredPermissions = List.copyOf(permissions);
        this.audit = AnnotationUtils.find(declaringType, method, Audit.class);
        this.idempotent = AnnotationUtils.find(declaringType, method, Idempotent.class);
        this.locked = AnnotationUtils.find(declaringType, method, Locked.class);
        this.rateLimited = AnnotationUtils.find(declaringType, method, RateLimited.class);
    }

    /**
     * 登记成功前必须满足的要求。
     *
     * @param requirement 当前执行产生的要求
     */
    public void addRequirement(CallRequirement requirement) {
        requirements.add(Objects.requireNonNull(requirement, "调用要求不能为空"));
    }

    /**
     * 登记权限判定携带的 Repository 资源范围。
     *
     * @param permission 携带范围的完整权限
     */
    public void registerResourceScope(Permission permission) {
        Permission checked = Objects.requireNonNull(permission, "权限不能为空");
        PermissionDecision decision = call.permissionDecision(checked)
                .filter(PermissionDecision::isAllowed)
                .filter(item -> item.scope().isPresent())
                .orElseThrow(() -> new IllegalStateException("当前权限没有可登记的资源范围: " + checked));
        ResourceScopeRequirement requirement = new ResourceScopeRequirement(
                checked,
                decision.scope().orElseThrow());
        if (resourceScopes.putIfAbsent(checked, requirement) == null) {
            addRequirement(requirement);
        }
    }

    /**
     * 消费当前执行为指定权限登记的资源范围。
     *
     * @param permission Repository 正在执行的完整权限
     * @param scopeType 业务模块定义的资源范围类型
     * @param <S> 资源范围类型
     * @return 当前执行没有登记该权限时为空，否则返回已消费的范围
     */
    public <S extends ResourceScope> Optional<S> consumeResourceScope(
            Permission permission,
            Class<S> scopeType) {
        ResourceScopeRequirement requirement = resourceScopes.get(Objects.requireNonNull(permission, "权限不能为空"));
        if (requirement == null) {
            return Optional.empty();
        }
        return Optional.of(requirement.consume(Objects.requireNonNull(scopeType, "资源范围类型不能为空")));
    }

    /** 校验实际业务方法成功前必须满足的全部要求。 */
    public void verifyRequirements() {
        requirements.forEach(CallRequirement::verifySatisfied);
    }

    /** 在只读或读写事务提交前校验适用于该事务的要求。 */
    public void verifyRequirementsBeforeCommit(boolean readOnly) {
        if (!invoking) {
            return;
        }
        requirements.stream()
                .filter(requirement -> !readOnly || requirement.appliesToReadOnlyTransaction())
                .forEach(CallRequirement::verifySatisfied);
    }

    /** 标记即将进入实际业务方法。 */
    public void beginInvocation() {
        invoking = true;
    }

    /** 标记实际业务方法已经离开。 */
    public void endInvocation() {
        invoking = false;
    }

    /** @param result 实际业务结果 */
    public void succeed(@Nullable Object result) {
        outcome = CallOutcome.success(result);
    }

    /** @param result 已保存的幂等结果 */
    public void replay(@Nullable Object result) {
        outcome = CallOutcome.replayed(result);
    }

    /** @param error 拒绝调用的业务异常 */
    public void deny(Throwable error) {
        outcome = CallOutcome.denied(error);
    }

    /** @param error 导致调用失败的异常 */
    public void fail(Throwable error) {
        outcome = CallOutcome.failed(error);
    }

    /** @return 当前传输调用 */
    @Contract(pure = true)
    public Call call() {
        return call;
    }

    /** @return 声明业务方法的具体类型 */
    @Contract(pure = true)
    public Class<?> declaringType() {
        return declaringType;
    }

    /** @return 被调用方法 */
    @Contract(pure = true)
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "CallExecution 的公开契约需要向声明解析器提供原始 Method")
    public Method method() {
        return method;
    }

    /** @return 方法实参副本 */
    @Contract(pure = true)
    public Object[] arguments() {
        return Arrays.copyOf(arguments, arguments.length);
    }

    /** @return 业务返回类型 */
    @Contract(pure = true)
    public Type resultType() {
        return resultType;
    }

    /** @return 当前方法允许匿名访问时返回 {@code true} */
    @Contract(pure = true)
    public boolean anonymous() {
        return anonymous;
    }

    /** @return 类型和方法声明的权限要求 */
    @Contract(pure = true)
    public List<RequirePermission> requiredPermissions() {
        return requiredPermissions;
    }

    /** @return 当前审计声明；未声明时为空 */
    @Contract(pure = true)
    public Optional<Audit> audit() {
        return Optional.ofNullable(audit);
    }

    /** @return 当前幂等声明；未声明时为空 */
    @Contract(pure = true)
    public Optional<Idempotent> idempotent() {
        return Optional.ofNullable(idempotent);
    }

    /** @return 当前分布式锁声明；未声明时为空 */
    @Contract(pure = true)
    public Optional<Locked> locked() {
        return Optional.ofNullable(locked);
    }

    /** @return 当前限流声明；未声明时为空 */
    @Contract(pure = true)
    public Optional<RateLimited> rateLimited() {
        return Optional.ofNullable(rateLimited);
    }

    /**
     * 返回当前方法执行结果。
     *
     * @return 已经得到结果时返回结果，否则为空
     */
    @Contract(pure = true)
    public Optional<CallOutcome> outcome() {
        return Optional.ofNullable(outcome);
    }

    /** 保存一项权限范围及其是否被 Repository 消费。 */
    private static final class ResourceScopeRequirement implements CallRequirement {

        private final Permission permission;

        private final ResourceScope scope;

        private boolean consumed;

        private ResourceScopeRequirement(Permission permission, ResourceScope scope) {
            this.permission = permission;
            this.scope = scope;
        }

        private <S extends ResourceScope> S consume(Class<S> scopeType) {
            if (!scopeType.isInstance(scope)) {
                throw new IllegalStateException(
                        "资源范围类型不匹配: 实际 " + scope.getClass().getName() + "，要求 " + scopeType.getName());
            }
            consumed = true;
            return scopeType.cast(scope);
        }

        @Override
        public void verifySatisfied() {
            if (!consumed) {
                throw new IllegalStateException("业务方法声明的数据权限范围没有被 Repository 消费: " + permission);
            }
        }
    }
}
