package com.sca4d.app.server.files;

import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.app.server.service.PlatformUserProfileService;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.permission.PermissionPolicy;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.storage.FileErrorCode;
import com.sca4d.core.api.storage.FileService;
import com.sca4d.core.api.storage.StoredFile;
import com.sca4d.iam.base.rbac.PlatformOperations;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 根据文件记录中的上传 Principal、scene 和目标 Resource 判断头像文件读写权限。 */
@Component
public class PlatformFilePermissionPolicy implements PermissionPolicy {
    private final @NotNull FileService files;

    /**
     * 创建读取实际文件记录的权限策略。
     *
     * @param files 文件服务
     */
    public PlatformFilePermissionPolicy(@NotNull FileService files) {
        this.files = Objects.requireNonNull(files, "文件服务不能为空");
    }

    /** {@inheritDoc} */
    @Override
    public boolean supports(@NotNull Resource resource) {
        return StoredFile.RESOURCE_TYPE.equals(resource.type());
    }

    /**
     * 完成上传时同时校验上传 Principal 和目标资料权限；读取时校验目标资料的本人或管理权限。
     *
     * @param call 已完成 Platform Principal 选择的调用
     * @param permission 目标文件和 READ 或 WRITE 动作
     * @return 文件属于头像场景且当前身份满足对应条件时允许，否则拒绝
     * @throws BusinessException READ 检查不允许继续时统一抛出文件不存在结果
     */
    @Override
    public @NotNull PermissionDecision decide(
            @NotNull Call call,
            @NotNull Permission permission) {
        if (!StoredFile.RESOURCE_TYPE.equals(permission.resource().type())) {
            return PermissionDecision.deny("文件权限策略只处理文件资源");
        }

        long fileId;
        try {
            fileId = Long.parseLong(permission.resource().id());
        } catch (NumberFormatException exception) {
            return denied(permission, "文件资源标识不是有效的数字");
        }
        if (fileId <= 0) {
            return denied(permission, "文件资源标识不是正数");
        }

        // 读取文件记录并确认它属于 App 定义的头像场景和用户资料资源。
        StoredFile file = files.loadById(fileId);
        if (!PlatformUserProfileService.PROFILE_AVATAR.equals(file.use().scene())
                || !PlatformUserProfile.RESOURCE_TYPE.equals(file.use().target().type())) {
            return denied(permission, "文件不属于 Platform 用户头像场景");
        }

        // 目标用户资料标识必须能够还原为 Platform Principal 标识。
        long targetPrincipalId;
        try {
            targetPrincipalId = Long.parseLong(file.use().target().id());
        } catch (NumberFormatException exception) {
            return denied(permission, "文件目标不是有效的 Platform Principal");
        }
        if (targetPrincipalId <= 0) {
            return denied(permission, "文件目标不是有效的 Platform Principal");
        }
        long currentPrincipalId = call.principal().orElseThrow().principalId();

        // 完成上传同时要求当前 Principal 是申请者，并允许本人或账号管理员维护目标资料。
        if (Action.WRITE.equals(permission.action())) {
            boolean uploaderMatches = file.uploaderPrincipalId() == currentPrincipalId;
            boolean targetAllowed = targetPrincipalId == currentPrincipalId
                    || call.grants(new Operation(PlatformOperations.ACCOUNTS_MANAGE));
            return uploaderMatches && targetAllowed
                    ? PermissionDecision.allow()
                    : PermissionDecision.deny("当前身份不能完成目标文件上传");
        }

        // 读取头像只检查目标资料范围，不要求当前 Principal 是文件创建者。
        if (Action.READ.equals(permission.action())) {
            boolean allowed = targetPrincipalId == currentPrincipalId
                    || call.grants(new Operation(PlatformOperations.ACCOUNTS_READ));
            return allowed
                    ? PermissionDecision.allow()
                    : denied(permission, "当前身份不能读取目标文件");
        }
        return PermissionDecision.deny("头像文件只支持读取和写入动作");
    }

    /**
     * 返回文件权限拒绝；读取动作统一隐藏文件状态、场景和权限原因。
     *
     * @param permission 当前文件权限
     * @param reason 写入动作可用于定位拒绝原因的内部说明
     * @return 非读取动作的拒绝结果
     * @throws BusinessException 读取动作不允许继续时统一抛出文件不存在结果
     */
    private static @NotNull PermissionDecision denied(
            @NotNull Permission permission,
            @NotNull String reason) {
        if (Action.READ.equals(permission.action())) {
            throw new BusinessException(FileErrorCode.NOT_FOUND);
        }
        return PermissionDecision.deny(reason);
    }
}
