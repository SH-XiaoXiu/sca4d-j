package com.sca4d.app.server.controllers;

import com.sca4d.app.server.audit.ApplicationAuditValues;
import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.platform.request.ReplaceAvatarRequest;
import com.sca4d.app.server.platform.request.ReplaceProfileRequest;
import com.sca4d.app.server.platform.view.ProfileView;
import com.sca4d.app.server.service.PlatformAccountApplicationService;
import com.sca4d.app.server.service.PlatformUserProfileService;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditPayload;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.iam.base.PlatformPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 读取和替换当前账号的示例 App 用户资料。 */
@RestController
@RequestMapping("/me/profile")
@Tag(name = "Current Account")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class ProfileController {

    private final @NotNull PlatformAccountApplicationService accounts;

    private final @NotNull PlatformUserProfileService profiles;

    /**
     * 创建当前账号资料入口。
     *
     * @param profiles App 用户资料服务
     * @param accounts Platform Account 应用服务
     */
    public ProfileController(
            @NotNull PlatformUserProfileService profiles,
            @NotNull PlatformAccountApplicationService accounts) {
        this.profiles = Objects.requireNonNull(profiles, "Platform 用户资料服务不能为空");
        this.accounts = Objects.requireNonNull(accounts, "Platform Account 应用服务不能为空");
    }

    /**
     * 查询当前 Principal 的 App 用户资料。
     *
     * @param principalId 当前 Principal 标识
     * @return 当前资料
     */
    @GetMapping
    @Operation(summary = "查询当前账号资料")
    public @NotNull ApiResponse<ProfileView> profile(
            @CurrentPrincipal @NotNull Long principalId) {
        PlatformUserProfile profile = profiles.loadByPrincipalId(principalId);
        return ApiResponse.ok(ProfileView.from(profile));
    }

    /**
     * 替换当前账号资料。
     *
     * @param principalId 当前 Principal 标识
     * @param request 新资料字段
     * @return 修改后的资料
     */
    @PutMapping
    @Operation(summary = "替换当前账号资料")
    @Audit(operation = "PLATFORM/PROFILE/REPLACE", payloadFactory = ApplicationAuditValues.MutationDetails.class)
    public @NotNull ApiResponse<ProfileView> replace(
            @CurrentPrincipal @NotNull @AuditTarget(resourceType = PlatformUserProfile.RESOURCE_TYPE_VALUE) Long principalId,
            @RequestBody @NotNull @AuditPayload ReplaceProfileRequest request) {
        PlatformUserProfile updated = accounts.replaceProfile(
                principalId,
                request.getDisplayName(),
                request.getEmail(),
                request.getPhoneNumber());
        return ApiResponse.ok(ProfileView.from(updated));
    }

    /**
     * 把当前账号上传完成的文件绑定为头像。
     *
     * @param principalId 当前 Principal 标识
     * @param request 头像文件标识
     * @return 修改后的资料
     */
    @PutMapping("/avatar")
    @Operation(summary = "替换当前账号头像")
    @Audit(operation = "PLATFORM/PROFILE/AVATAR/REPLACE", payloadFactory = ApplicationAuditValues.MutationDetails.class)
    public @NotNull ApiResponse<ProfileView> replaceAvatar(
            @CurrentPrincipal @NotNull @AuditTarget(resourceType = PlatformUserProfile.RESOURCE_TYPE_VALUE) Long principalId,
            @RequestBody @NotNull @AuditPayload ReplaceAvatarRequest request) {
        PlatformUserProfile updated = profiles.replaceAvatar(
                principalId,
                new PlatformPrincipal(principalId),
                request.getFileId());
        return ApiResponse.ok(ProfileView.from(updated));
    }

    /**
     * 删除当前账号头像。
     *
     * @param principalId 当前 Principal 标识
     * @return 修改后的资料
     */
    @DeleteMapping("/avatar")
    @Operation(summary = "删除当前账号头像")
    @Audit(operation = "PLATFORM/PROFILE/AVATAR/DELETE")
    public @NotNull ApiResponse<ProfileView> deleteAvatar(
            @CurrentPrincipal @NotNull @AuditTarget(resourceType = PlatformUserProfile.RESOURCE_TYPE_VALUE) Long principalId) {
        PlatformUserProfile updated = profiles.deleteAvatar(principalId);
        return ApiResponse.ok(ProfileView.from(updated));
    }
}
