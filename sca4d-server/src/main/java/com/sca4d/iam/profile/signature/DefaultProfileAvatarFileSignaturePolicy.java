package com.sca4d.iam.profile.signature;

import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.file.FileSignatureOperation;
import com.sca4d.file.error.FileErrorCode;
import com.sca4d.file.signature.FileSignatureItem;
import com.sca4d.file.signature.FileSignaturePolicy;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.profile.DefaultProfile;
import com.sca4d.iam.profile.DefaultProfileAvatar;
import com.sca4d.iam.profile.repository.DefaultProfileRepository;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.tenant.TenantPermissions;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** DEFAULT Profile 头像上传和读取的文件签名授权策略。 */
@Component
@ConditionalOnProperty(prefix = "sca4dj.file", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DefaultProfileAvatarFileSignaturePolicy implements FileSignaturePolicy {

    private final DefaultProfileRepository defaultProfileRepository;

    @Override
    public @NotNull Set<String> scenes() {
        return Set.of(DefaultProfileAvatar.FILE_SCENE);
    }

    @Override
    public void authorize(@NotNull CurrentRequest auth, @NotNull List<FileSignatureItem> items) {
        if (!auth.authenticated()) {
            throw new UnauthenticatedException();
        }
        Principal principal = auth.requirePrincipal();
        SessionPermission permission = auth.requireSession().requirePermission(principal.requirePrincipalId());
        Set<Long> subjectIds = new LinkedHashSet<>();
        for (FileSignatureItem item : items) {
            if (item.getOperation() == FileSignatureOperation.UPLOAD) {
                if (item.getSizeBytes() == null) {
                    throw new BizException(FileErrorCode.FILE_SIGNATURE_BATCH_INVALID);
                }
                DefaultProfileAvatar.requireSupportedFile(item.getContentType(), item.getSizeBytes());
            }
            if (StringUtils.hasText(item.getResourceId())) {
                try {
                    subjectIds.add(Long.valueOf(item.getResourceId()));
                } catch (RuntimeException exception) {
                    throw new BizException(IamErrorCode.DEFAULT_PROFILE_RESOURCE_ID_INVALID);
                }
            } else if (item.getOperation() == FileSignatureOperation.READ) {
                throw new BizException(IamErrorCode.DEFAULT_PROFILE_RESOURCE_ID_INVALID);
            }
        }
        Map<Long, DefaultProfile> profiles = defaultProfileRepository.findBySubjectIds(subjectIds);
        for (FileSignatureItem item : items) {
            Long subjectId = StringUtils.hasText(item.getResourceId()) ? Long.valueOf(item.getResourceId()) : null;
            if (item.getOperation() == FileSignatureOperation.READ) {
                DefaultProfile profile = profiles.get(subjectId);
                if (profile == null || profile.getAvatarFileId() == null || !profile.getAvatarFileId().equals(item.getFileId())) {
                    throw new BizException(FileErrorCode.FILE_NOT_FOUND);
                }
                continue;
            }
            if (subjectId == null) {
                Set<String> permissions = permission.getPermissionCodes();
                if ((principal.requirePrincipalType() == PrincipalType.PLATFORM
                        && (permissions.contains(PlatformPermissions.ACCOUNTS_CREATE)
                                || permissions.contains(PlatformPermissions.TENANT_MEMBERS_CREATE)))
                        || (principal.requirePrincipalType() == PrincipalType.TENANT
                                && permissions.contains(TenantPermissions.TENANT_MEMBERS_CREATE))) {
                    continue;
                }
                throw new ForbiddenException();
            }
            boolean self = auth.requireSubject().getSubjectId().equals(subjectId);
            boolean platformAdministrator = principal.requirePrincipalType() == PrincipalType.PLATFORM
                    && permission.getPermissionCodes().contains(PlatformPermissions.ACCOUNTS_UPDATE);
            if ((!self && !platformAdministrator) || !profiles.containsKey(subjectId)) {
                throw new ForbiddenException();
            }
        }
    }
}
