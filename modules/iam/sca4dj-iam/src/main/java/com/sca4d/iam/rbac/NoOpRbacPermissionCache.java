package com.sca4d.iam.rbac;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 在应用没有安装 Redis 时让 RBAC 直接读取 Repository。 */
public class NoOpRbacPermissionCache implements RbacPermissionCache {

    public NoOpRbacPermissionCache() {
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull RbacPermissionCacheLookup find(
            @NotNull Map<@NotNull RbacScope, ? extends Collection<@NotNull Long>> principalIdsByScope) {
        Objects.requireNonNull(principalIdsByScope, "RBAC 批量缓存输入不能为空");
        return new RbacPermissionCacheLookup(Map.of(), Map.of());
    }

    /** {@inheritDoc} */
    @Override
    public void put(
            @NotNull RbacPermissionCacheLookup lookup,
            @NotNull Map<@NotNull RbacScope, ? extends Map<@NotNull Long, ? extends List<@NotNull PermissionSelector>>> selectorsByScope) {
        Objects.requireNonNull(lookup, "缓存读取结果不能为空");
        Objects.requireNonNull(selectorsByScope, "待缓存权限选择符不能为空");
    }

    /** {@inheritDoc} */
    @Override
    public void evict(
            @NotNull Map<@NotNull RbacScope, ? extends Collection<@NotNull Long>> principalIdsByScope) {
        Objects.requireNonNull(principalIdsByScope, "RBAC 批量缓存输入不能为空");
    }
}
