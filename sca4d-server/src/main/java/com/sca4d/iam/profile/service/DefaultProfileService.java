package com.sca4d.iam.profile.service;

import com.sca4d.authentication.AuthenticationClientStateTargets;
import com.sca4d.authentication.SessionService;
import com.sca4d.common.concurrent.lock.DistributedLock;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.web.ClientState;
import com.sca4d.file.FileResource;
import com.sca4d.file.FileService;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.profile.DefaultProfile;
import com.sca4d.iam.profile.DefaultProfileAvatar;
import com.sca4d.iam.profile.repository.DefaultProfileRepository;
import com.sca4d.iam.subject.SubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 读取和修改 Subject 的 DEFAULT Profile 头像。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultProfileService {

    private final DefaultProfileRepository defaultProfileRepository;
    private final SubjectService subjectService;
    private final FileService fileService;
    private final SessionService sessionService;

    /**
     * 读取 Subject 的 DEFAULT Profile。
     *
     * @param subjectId Subject 主键
     * @return 未逻辑删除的默认资料
     * @throws BizException DEFAULT Principal 或对应资料行不存在时抛出
     */
    public @NotNull DefaultProfile require(@NotNull Long subjectId) {
        DefaultProfile profile = defaultProfileRepository.findBySubjectId(subjectId);
        if (profile == null) {
            throw new BizException(IamErrorCode.DEFAULT_PROFILE_NOT_FOUND);
        }
        return profile;
    }

    /**
     * 修改 Subject 默认展示名。
     *
     * @param subjectId Subject 主键
     * @param displayName 新默认展示名
     * @param subjectVersion 调用方读取到的 Subject 版本
     */
    @Transactional
    @DistributedLock(key = "'subject:' + #subjectId")
    public void updateDisplayName(
            @NotNull Long subjectId, @NotNull String displayName, @NotNull Long subjectVersion) {
        require(subjectId);
        if (displayName.isBlank()) {
            throw new BizException(IamErrorCode.SUBJECT_DISPLAY_NAME_REQUIRED);
        }
        subjectService.update(subjectId, displayName.trim(), subjectVersion);
        sessionService.refreshAfterCommit(List.of(subjectId));
        ClientState.refresh(AuthenticationClientStateTargets.ME);
        log.info(
                "Subject 默认展示名已更新 | subjectId={} subjectVersion={}",
                subjectId,
                subjectVersion + 1);
    }

    /**
     * 替换 Subject 的 DEFAULT Profile 头像，并将旧文件加入删除队列。
     *
     * @param subjectId DEFAULT Profile 所属 Subject
     * @param avatarFileId 已完成上传的头像文件
     * @param avatarUploadGrant 文件消费授权
     * @param version 调用方读取到的默认资料版本
     * @param actorPrincipalId 上传文件所属 Principal
     */
    @Transactional
    @DistributedLock(key = "'subject:' + #subjectId")
    public void replaceAvatar(
            @NotNull Long subjectId,
            @NotNull Long avatarFileId,
            @NotNull String avatarUploadGrant,
            @NotNull Long version,
            @NotNull Long actorPrincipalId) {
        DefaultProfile profile = require(subjectId);
        FileResource avatar = fileService.requireReadyFile(avatarFileId, actorPrincipalId);
        DefaultProfileAvatar.requireSupportedFile(avatar.getContentType(), avatar.getSizeBytes());
        fileService.consumeFile(
                avatarFileId,
                avatarUploadGrant,
                DefaultProfileAvatar.FILE_SCENE,
                subjectId.toString(),
                actorPrincipalId);
        defaultProfileRepository.updateAvatar(subjectId, avatarFileId, version);
        if (profile.getAvatarFileId() != null) {
            fileService.requestDelete(profile.getAvatarFileId());
        }
        sessionService.refreshAfterCommit(List.of(subjectId));
        ClientState.refresh(AuthenticationClientStateTargets.ME);
        log.info(
                "DEFAULT Profile 头像替换已写入事务 | subjectId={} avatarFileId={} previousAvatarFileId={} version={}",
                subjectId,
                avatarFileId,
                profile.getAvatarFileId(),
                version + 1);
    }

    /**
     * 清空 Subject 的 DEFAULT Profile 头像，并将原文件加入删除队列。
     *
     * @param subjectId DEFAULT Profile 所属 Subject
     * @param version 调用方读取到的默认资料版本
     */
    @Transactional
    @DistributedLock(key = "'subject:' + #subjectId")
    public void removeAvatar(@NotNull Long subjectId, @NotNull Long version) {
        DefaultProfile profile = require(subjectId);
        defaultProfileRepository.updateAvatar(subjectId, null, version);
        if (profile.getAvatarFileId() != null) {
            fileService.requestDelete(profile.getAvatarFileId());
        }
        sessionService.refreshAfterCommit(List.of(subjectId));
        ClientState.refresh(AuthenticationClientStateTargets.ME);
        log.info(
                "DEFAULT Profile 头像移除已写入事务 | subjectId={} previousAvatarFileId={} version={}",
                subjectId,
                profile.getAvatarFileId(),
                version + 1);
    }
}
