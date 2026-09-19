package com.sca4d.iam.rbac;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 缓存 Principal 在具体 RBAC 范围中通过启用角色得到的权限选择符。 */
public interface RbacPermissionCache {

    /**
     * 批量读取已经缓存的权限选择符。
     *
     * @param principalIdsByScope 待读取的 RBAC 范围和 Principal 标识
     * @return 缓存命中项和用于并发安全回填的修订号
     */
    @CheckReturnValue
    @NotNull
    RbacPermissionCacheLookup find(
            @NotNull Map<@NotNull RbacScope, ? extends Collection<@NotNull Long>> principalIdsByScope);

    /**
     * 批量保存数据库已经确认的权限选择符，包括空授权结果。
     *
     * <p>实现只能回填 {@code lookup} 中存在修订号且读取后没有发生失效的坐标。
     *
     * @param lookup 触发本次 Repository 回源的缓存读取结果
     * @param selectorsByScope Repository 已经确认的权限选择符
     */
    void put(
            @NotNull RbacPermissionCacheLookup lookup,
            @NotNull Map<@NotNull RbacScope, ? extends Map<@NotNull Long, ? extends List<@NotNull PermissionSelector>>> selectorsByScope);

    /** 批量精确删除角色或关系变化所影响的 Principal 缓存。 */
    void evict(@NotNull Map<@NotNull RbacScope, ? extends Collection<@NotNull Long>> principalIdsByScope);
}
