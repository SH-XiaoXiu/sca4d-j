package com.sca4d.app.server.files;

import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.permission.PermissionPolicy;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.iam.base.rbac.PlatformOperations;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/** 判断当前 Platform Principal 能否为目标用户资料申请头像上传。 */
@Component
public class PlatformUserProfilePermissionPolicy implements PermissionPolicy {
    /** 创建无状态的 Platform 用户资料权限策略。 */
    public PlatformUserProfilePermissionPolicy() {
    }

    /** {@inheritDoc} */
    @Override
    public boolean supports(@NotNull Resource resource) {
        return PlatformUserProfile.RESOURCE_TYPE.equals(resource.type());
    }

    /**
     * 允许资料所属 Principal 写入自己的资料；具有账号维护操作权限的 Principal 可以写入其他用户资料。
     *
     * @param call 已完成 Platform Principal 选择的调用
     * @param permission 目标用户资料与动作
     * @return 满足本人或管理员条件时允许，否则拒绝
     */
    @Override
    public @NotNull PermissionDecision decide(
            @NotNull Call call,
            @NotNull Permission permission) {
        if (!PlatformUserProfile.RESOURCE_TYPE.equals(permission.resource().type())
                || !Action.WRITE.equals(permission.action())) {
            return PermissionDecision.deny("Platform 用户资料头像只支持写入动作");
        }

        long targetPrincipalId;
        try {
            targetPrincipalId = Long.parseLong(permission.resource().id());
        } catch (NumberFormatException exception) {
            return PermissionDecision.deny("Platform 用户资料标识不是有效的数字");
        }

        // 资料所属 Principal 可以申请自己的头像；账号管理员可以为其他 Platform 用户资料申请头像。
        long currentPrincipalId = call.principal().orElseThrow().principalId();
        boolean allowed = targetPrincipalId == currentPrincipalId
                || call.grants(new Operation(PlatformOperations.ACCOUNTS_MANAGE));
        return allowed
                ? PermissionDecision.allow()
                : PermissionDecision.deny("当前 Principal 不能写入目标用户资料");
    }
}
