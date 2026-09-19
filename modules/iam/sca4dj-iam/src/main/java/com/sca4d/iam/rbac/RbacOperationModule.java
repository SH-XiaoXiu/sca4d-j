package com.sca4d.iam.rbac;

import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationModule;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

/** 声明一个 Operation 模块可以把自己的具体 Operation 授予哪些 RBAC 领域。 */
public interface RbacOperationModule extends OperationModule {

    /**
     * 返回当前模块参与的 RBAC 领域。
     *
     * @return 至少包含一个领域的不可变集合
     */
    @NotNull
    @Unmodifiable
    Set<@NotNull RbacDomain> domains();

    /**
     * 判断当前模块的一项 Operation 能否授予指定 RBAC 领域。
     *
     * <p>默认允许授予 {@link #domains()} 声明的全部领域；同一模块内不同 Operation 边界不同时覆盖本方法。
     *
     * @param domain 待授权 RBAC 领域
     * @param operation 必须属于当前模块的具体 Operation
     * @return 可以由该领域角色保存时为 {@code true}
     */
    @CheckReturnValue
    default boolean canGrant(@NotNull RbacDomain domain, @NotNull Operation operation) {
        return domains().contains(domain);
    }
}
