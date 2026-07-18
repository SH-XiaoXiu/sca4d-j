package com.sca4d.permission.satoken;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpUtil;
import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.permission.PermissionChecker;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * 基于 Sa-Token RBAC 规则的 PermissionChecker。
 */
@Component
public class SaTokenPermissionChecker implements PermissionChecker {

    @Override
    public @NotNull SessionPermission require(@NotNull CurrentRequest request) {
        Long principalId = request.requirePrincipal().requirePrincipalId();
        SessionPermission permission = request.requireSession().requirePermission(principalId);
        request.setSessionPermission(permission);
        return permission;
    }

    @Override
    public void requirePermission(
            @NotNull CurrentRequest request, @NotNull String permissionCode) {
        require(request);
        try {
            StpUtil.checkPermission(permissionCode);
        } catch (SaTokenException exception) {
            throw new ForbiddenException(CommonErrorCode.PERMISSION_MISSING_PERMISSION, permissionCode);
        }
    }

    @Override
    public void requireRole(@NotNull CurrentRequest request, @NotNull String roleCode) {
        require(request);
        try {
            StpUtil.checkRole(roleCode);
        } catch (SaTokenException exception) {
            throw new ForbiddenException(CommonErrorCode.PERMISSION_MISSING_ROLE, roleCode);
        }
    }
}
