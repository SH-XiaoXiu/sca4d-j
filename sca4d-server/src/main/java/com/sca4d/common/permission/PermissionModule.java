package com.sca4d.common.permission;

import org.jetbrains.annotations.NotNull;

/**
 * 权限模块。
 */
public interface PermissionModule {

    /**
     * 获取当前模块的权限树。
     *
     * @return 权限树根节点
     */
    @NotNull
    PermissionNode permissionTree();
}
