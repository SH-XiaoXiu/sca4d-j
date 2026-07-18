package com.sca4d.common.permission;

import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;

/**
 * 检查当前 Principal 是否拥有指定角色和功能权限。
 */
public interface PermissionChecker {

    /**
     * 加载并写入当前请求的 SessionPermission。
     *
     * @param request 当前请求
     * @return 当前会话权限
     */
    @NotNull
    SessionPermission require(@NotNull CurrentRequest request);

    /**
     * 要求当前 Principal 具备指定权限。
     *
     * @param request 当前请求
     * @param permissionCode 权限编码
     */
    void requirePermission(
            @NotNull CurrentRequest request, @NotNull String permissionCode);

    /**
     * 要求当前 Principal 具备指定角色。
     *
     * @param request 当前请求
     * @param roleCode 角色编码
     */
    void requireRole(@NotNull CurrentRequest request, @NotNull String roleCode);
}
