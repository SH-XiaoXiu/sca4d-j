package com.sca4d.iam.rbac;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/** 在角色或 Principal 角色关系发生变化时，标记受影响 Principal 关联的 Account Session。 */
public interface RbacSessionInvalidation {

    /**
     * 返回当前实现负责的 RBAC 领域。
     *
     * @return RBAC 领域
     */
    @CheckReturnValue
    @NotNull
    RbacDomain domain();

    /**
     * 立即标记关联 Session，使之后的请求重新读取权限。
     *
     * @param scope 发生变化的角色空间
     * @param principalIds 角色或 Principal 角色关系发生变化的 Principal
     */
    void invalidate(
            @NotNull RbacScope scope,
            @NotNull Collection<@NotNull Long> principalIds);
}
