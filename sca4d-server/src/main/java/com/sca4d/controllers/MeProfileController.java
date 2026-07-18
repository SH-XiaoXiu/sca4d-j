package com.sca4d.controllers;

import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.iam.profile.audit.DefaultProfileAuditOperations;
import com.sca4d.iam.profile.request.DefaultProfileAvatarDeleteRequest;
import com.sca4d.iam.profile.request.DefaultProfileAvatarUpdateRequest;
import com.sca4d.iam.profile.request.DefaultProfileUpdateRequest;
import com.sca4d.iam.profile.service.DefaultProfileService;
import com.sca4d.iam.resource.IamResourceTypes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 修改当前 Subject 的默认资料。 */
@RestController
@RequestMapping("/me/profile")
@RequiredArgsConstructor
public class MeProfileController {

    private final DefaultProfileService defaultProfileService;

    /**
     * 修改当前 Subject 的默认展示名。
     *
     * @param request 展示名和 Subject 版本
     * @param subject 当前认证 Subject
     * @return 没有响应数据的成功结果
     */
    @PutMapping
    @Audit(module = DefaultProfileAuditOperations.DEFAULT_PROFILE, action = DefaultProfileAuditOperations.UPDATE_DISPLAY_NAME, targetType = IamResourceTypes.SUBJECT, targetId = "#subject.subjectId")
    public @NotNull ApiResponse<Void> update(
            @Valid @RequestBody @NotNull DefaultProfileUpdateRequest request,
            @AuthSubject @NotNull Subject subject) {
        defaultProfileService.updateDisplayName(
                subject.getSubjectId(), request.getDisplayName(), request.getSubjectVersion());
        return ApiResponse.ok();
    }

    /**
     * 替换当前 Subject 的 DEFAULT Profile 头像。
     *
     * @param request 文件消费授权和默认资料版本
     * @param subject 当前认证 Subject
     * @return 没有响应数据的成功结果
     */
    @PutMapping("/avatar")
    @Audit(module = DefaultProfileAuditOperations.DEFAULT_PROFILE, action = DefaultProfileAuditOperations.REPLACE_AVATAR, targetType = IamResourceTypes.SUBJECT, targetId = "#subject.subjectId")
    public @NotNull ApiResponse<Void> replaceAvatar(
            @Valid @RequestBody @NotNull DefaultProfileAvatarUpdateRequest request,
            @AuthSubject @NotNull Subject subject,
            @AuthPrincipal @NotNull Principal principal) {
        defaultProfileService.replaceAvatar(
                subject.getSubjectId(),
                request.getFileId(),
                request.getUploadGrant(),
                request.getProfileVersion(),
                principal.requirePrincipalId());
        return ApiResponse.ok();
    }

    /**
     * 清空当前 Subject 的 DEFAULT Profile 头像。
     *
     * @param request 当前默认资料版本
     * @param subject 当前认证 Subject
     * @return 没有响应数据的成功结果
     */
    @DeleteMapping("/avatar")
    @Audit(module = DefaultProfileAuditOperations.DEFAULT_PROFILE, action = DefaultProfileAuditOperations.REMOVE_AVATAR, targetType = IamResourceTypes.SUBJECT, targetId = "#subject.subjectId")
    public @NotNull ApiResponse<Void> removeAvatar(
            @Valid @RequestBody @NotNull DefaultProfileAvatarDeleteRequest request,
            @AuthSubject @NotNull Subject subject) {
        defaultProfileService.removeAvatar(subject.getSubjectId(), request.getProfileVersion());
        return ApiResponse.ok();
    }
}
