package com.sca4d.common.permission;

import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;

/**
 * 没有注册权限检查实现时使用的默认拒绝实现。
 *
 * <p>应用没有提供授权实现时，任何权限要求都会被拒绝访问。
 */
public class PermissionCheckerUnavailable implements PermissionChecker {

    @Override
    public @NotNull SessionPermission require(@NotNull CurrentRequest request) {
        throw new ForbiddenException(CommonErrorCode.PERMISSION_CHECKER_UNAVAILABLE);
    }

    @Override
    public void requirePermission(@NotNull CurrentRequest request, @NotNull String permissionCode) {
        throw new ForbiddenException(CommonErrorCode.PERMISSION_CHECKER_UNAVAILABLE);
    }

    @Override
    public void requireRole(@NotNull CurrentRequest request, @NotNull String roleCode) {
        throw new ForbiddenException(CommonErrorCode.PERMISSION_CHECKER_UNAVAILABLE);
    }
}
