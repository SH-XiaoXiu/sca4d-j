package com.sca4d.core.api.call;

import com.sca4d.core.api.audit.AuditCallInterceptor;
import com.sca4d.core.api.concurrent.IdempotencyCallInterceptor;
import com.sca4d.core.api.concurrent.LockCallInterceptor;
import com.sca4d.core.api.concurrent.RateLimitCallInterceptor;
import com.sca4d.core.api.identity.IdentityCallInterceptor;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.PermissionCallInterceptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 集中登记 Call 阶段、Interceptor 和依赖关系。 */
public final class CallLifecycleConfiguration {

    private final Map<TreeCode, Registration> registrations = new LinkedHashMap<>();

    /** 从已注册的环绕处理创建默认配置；处理自身的依赖会作为初始约束。 */
    public CallLifecycleConfiguration(@NotNull List<? extends CallLifecycle.Around> interceptors) {
        for (CallLifecycle.Around interceptor : Objects.requireNonNull(interceptors)) {
            register(
                    CallLifecycle.AROUND,
                    interceptor,
                    interceptor.after(),
                    interceptor.before(),
                    interceptor.priority());
        }
        applyCoreOrder();
    }

    /** 固定 Core 内置环绕节点顺序；可选节点不存在时保留其余顺序。 */
    public void applyCoreOrder() {
        afterIfRegistered(IdentityCallInterceptor.CODE, AuditCallInterceptor.CODE);
        afterIfRegistered(PermissionCallInterceptor.CODE, IdentityCallInterceptor.CODE);
        afterIfRegistered(RateLimitCallInterceptor.CODE, PermissionCallInterceptor.CODE);
        afterIfRegistered(IdempotencyCallInterceptor.CODE, RateLimitCallInterceptor.CODE);
        afterIfRegistered(LockCallInterceptor.CODE, IdempotencyCallInterceptor.CODE);
    }

    /** 登记一个阶段参与者。 */
    public void register(
            @NotNull CallLifecycle lifecycle,
            @NotNull CallLifecycle.Around interceptor,
            @NotNull Set<TreeCode> after,
            @NotNull Set<TreeCode> before,
            int priority) {
        CallLifecycle checkedLifecycle = Objects.requireNonNull(lifecycle, "调用阶段不能为空");
        CallLifecycle.Around checkedInterceptor = Objects.requireNonNull(interceptor, "调用拦截器不能为空");
        TreeCode code = Objects.requireNonNull(checkedInterceptor.code(), "调用拦截器编码不能为空");
        if (registrations.containsKey(code)) {
            throw new IllegalArgumentException("调用拦截器编码重复: " + code);
        }
        registrations.put(
                code,
                new Registration(
                        checkedLifecycle,
                        checkedInterceptor,
                        new LinkedHashSet<>(Objects.requireNonNull(after)),
                        new LinkedHashSet<>(Objects.requireNonNull(before)),
                        priority));
    }

    /** 追加节点的后置依赖；当前节点必须排在 dependency 之后。 */
    public void after(@NotNull TreeCode code, @NotNull TreeCode dependency) {
        registration(code).after.add(Objects.requireNonNull(dependency, "依赖编码不能为空"));
    }

    /** 两个节点都已安装时追加可选依赖；可选功能未安装时不改变其余顺序。 */
    public void afterIfRegistered(@NotNull TreeCode code, @NotNull TreeCode dependency) {
        if (registrations.containsKey(code) && registrations.containsKey(dependency)) {
            after(code, dependency);
        }
    }

    /** 追加节点的前置依赖；当前节点必须排在 dependency 之前。 */
    public void before(@NotNull TreeCode code, @NotNull TreeCode dependency) {
        registration(code).before.add(Objects.requireNonNull(dependency, "依赖编码不能为空"));
    }

    /** 返回不可修改的配置快照。 */
    public @NotNull @Unmodifiable List<Registration> registrations() {
        return List.copyOf(registrations.values());
    }

    private Registration registration(TreeCode code) {
        Registration registration = registrations.get(Objects.requireNonNull(code, "节点编码不能为空"));
        if (registration == null) {
            throw new IllegalArgumentException("调用拦截器编码未注册: " + code);
        }
        return registration;
    }

    /** 保存一个已登记的阶段参与者及其排序约束。 */
    public static final class Registration {
        private final CallLifecycle lifecycle;

        private final CallLifecycle.Around interceptor;

        private final Set<TreeCode> after;

        private final Set<TreeCode> before;

        private final int priority;

        private Registration(
                CallLifecycle lifecycle,
                CallLifecycle.Around interceptor,
                Set<TreeCode> after,
                Set<TreeCode> before,
                int priority) {
            this.lifecycle = lifecycle;
            this.interceptor = interceptor;
            this.after = after;
            this.before = before;
            this.priority = priority;
        }

        /** @return 当前节点生命周期语义 */
        public @NotNull CallLifecycle lifecycle() {
            return lifecycle;
        }

        /** @return 当前环绕实现 */
        public @NotNull CallLifecycle.Around interceptor() {
            return interceptor;
        }

        /** @return 当前节点必须位于其后的节点 */
        public @NotNull @Unmodifiable Set<TreeCode> after() {
            return Set.copyOf(after);
        }

        /** @return 当前节点必须位于其前的节点 */
        public @NotNull @Unmodifiable Set<TreeCode> before() {
            return Set.copyOf(before);
        }

        /** @return 无硬依赖时使用的优先级 */
        public int priority() {
            return priority;
        }
    }
}
