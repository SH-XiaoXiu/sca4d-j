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

    /** 模块或应用在执行器构建前补充生命周期依赖。 */
    @FunctionalInterface
    public interface Customizer {
        /** 只修改本次应用的节点或阶段依赖，不执行调用逻辑。 */
        void configure(@NotNull CallLifecycleConfiguration configuration);
    }

    private final Map<TreeCode, Registration> registrations = new LinkedHashMap<>();

    /**
     * 从已注册的生命周期参与者创建默认配置；参与者自身声明的 before/after 依赖作为初始约束，并应用内置顺序。
     *
     * @param interceptors 生命周期参与者，每个参与者只能实现一种阶段回调，编码不能重复
     * @throws IllegalArgumentException 参与者没有阶段回调、实现多个阶段或编码重复时抛出
     */
    public CallLifecycleConfiguration(@NotNull List<? extends CallLifecycle.Participant> interceptors) {
        for (CallLifecycle.Participant interceptor : Objects.requireNonNull(interceptors)) {
            register(
                    lifecycle(interceptor),
                    interceptor,
                    interceptor.after(),
                    interceptor.before(),
                    interceptor.priority());
        }
        applyCoreOrder();
    }

    /** 固定 Core 内置环绕节点顺序；可选节点不存在时保留其余顺序。 */
    public void applyCoreOrder() {
        if (registrations.containsKey(AuditCallInterceptor.CODE)) {
            for (TreeCode code : registrations.keySet()) {
                if (!code.equals(AuditCallInterceptor.CODE)) {
                    after(code, AuditCallInterceptor.CODE);
                }
            }
        }
        afterIfRegistered(PermissionCallInterceptor.CODE, IdentityCallInterceptor.CODE);
        afterIfRegistered(IdentityCallInterceptor.PrincipalRequirement.CODE, IdentityCallInterceptor.CODE);
        afterIfRegistered(PermissionCallInterceptor.CODE, IdentityCallInterceptor.PrincipalRequirement.CODE);
        afterIfRegistered(RateLimitCallInterceptor.CODE, PermissionCallInterceptor.CODE);
        afterIfRegistered(IdempotencyCallInterceptor.CODE, RateLimitCallInterceptor.CODE);
        afterIfRegistered(LockCallInterceptor.CODE, IdempotencyCallInterceptor.CODE);
    }

    /**
     * 登记一个阶段参与者及其排序约束。
     *
     * @param lifecycle 参与者声明的阶段，必须与其实现的回调接口一致
     * @param interceptor 阶段参与者，编码在配置中必须唯一
     * @param after 当前节点必须排在其之后的节点编码
     * @param before 当前节点必须排在其之前的节点编码
     * @param priority 无硬依赖时的排序优先级
     * @throws IllegalArgumentException 阶段与回调类型不一致或编码重复时抛出
     */
    public void register(
            @NotNull CallLifecycle lifecycle,
            @NotNull CallLifecycle.Participant interceptor,
            @NotNull Set<TreeCode> after,
            @NotNull Set<TreeCode> before,
            int priority) {
        CallLifecycle checkedLifecycle = Objects.requireNonNull(lifecycle, "调用阶段不能为空");
        CallLifecycle.Participant checkedInterceptor = Objects.requireNonNull(interceptor, "调用拦截器不能为空");
        if (checkedLifecycle != lifecycle(checkedInterceptor)) {
            throw new IllegalArgumentException("调用阶段与回调类型不匹配: " + checkedLifecycle + "/" + checkedInterceptor.code());
        }
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

    /** 当前节点必须排在指定阶段的全部节点之后。 */
    public void after(@NotNull TreeCode code, @NotNull CallLifecycle lifecycle) {
        Registration current = registration(code);
        for (Registration candidate : registrations.values()) {
            if (candidate != current && candidate.lifecycle == lifecycle) {
                current.after.add(candidate.interceptor.code());
            }
        }
    }

    /** 当前节点必须排在指定阶段的全部节点之前。 */
    public void before(@NotNull TreeCode code, @NotNull CallLifecycle lifecycle) {
        Registration current = registration(code);
        for (Registration candidate : registrations.values()) {
            if (candidate != current && candidate.lifecycle == lifecycle) {
                current.before.add(candidate.interceptor.code());
            }
        }
    }

    /** 返回不可修改的配置快照。 */
    public @NotNull @Unmodifiable List<Registration> registrations() {
        return List.copyOf(registrations.values());
    }

    /**
     * 按编码查找已登记的节点。
     *
     * @param code 节点编码
     * @return 已登记节点的可修改依赖视图
     * @throws IllegalArgumentException 编码未登记时抛出
     */
    private Registration registration(TreeCode code) {
        Registration registration = registrations.get(Objects.requireNonNull(code, "节点编码不能为空"));
        if (registration == null) {
            throw new IllegalArgumentException("调用拦截器编码未注册: " + code);
        }
        return registration;
    }

    /** 接口类型直接确定阶段；不接受一个对象实现多个不同阶段。 */
    private static CallLifecycle lifecycle(CallLifecycle.Participant participant) {
        CallLifecycle selected = null;
        if (participant instanceof CallLifecycle.Around)
            selected = CallLifecycle.AROUND;
        if (participant instanceof CallLifecycle.Before)
            selected = single(selected, CallLifecycle.BEFORE);
        if (participant instanceof CallLifecycle.AfterReturning)
            selected = single(selected, CallLifecycle.AFTER_RETURNING);
        if (participant instanceof CallLifecycle.AfterThrowing)
            selected = single(selected, CallLifecycle.AFTER_THROWING);
        if (participant instanceof CallLifecycle.After)
            selected = single(selected, CallLifecycle.AFTER);
        if (participant instanceof CallLifecycle.AfterCommit)
            selected = single(selected, CallLifecycle.AFTER_COMMIT);
        if (participant instanceof CallLifecycle.Completion)
            selected = single(selected, CallLifecycle.COMPLETION);
        if (selected == null) {
            throw new IllegalArgumentException("调用参与者没有阶段回调: " + participant.code());
        }
        return selected;
    }

    /**
     * 确认参与者只实现一种阶段回调，并返回该阶段。
     *
     * @param previous 已经为同一参与者选中的阶段，尚未选中时为 {@code null}
     * @param selected 本次检查到的阶段
     * @return 本次选中的阶段
     * @throws IllegalArgumentException 已经选中另一阶段时抛出
     */
    private static CallLifecycle single(CallLifecycle previous, CallLifecycle selected) {
        if (previous != null) {
            throw new IllegalArgumentException("一个调用参与者只能实现一个阶段回调: " + previous + "/" + selected);
        }
        return selected;
    }

    /** 保存一个已登记的阶段参与者及其排序约束。 */
    public static final class Registration {
        private final CallLifecycle lifecycle;

        private final CallLifecycle.Participant interceptor;

        private final Set<TreeCode> after;

        private final Set<TreeCode> before;

        private final int priority;

        private Registration(
                CallLifecycle lifecycle,
                CallLifecycle.Participant interceptor,
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
        public @NotNull CallLifecycle.Participant interceptor() {
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
