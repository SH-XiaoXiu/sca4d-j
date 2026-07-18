package com.sca4d.iam.account.view;

import com.sca4d.file.view.FileSignatureRefView;
import com.sca4d.iam.account.ManagedAccountProfile;
import com.sca4d.iam.profile.DefaultProfileAvatar;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 管理端可见的账号通用资料，不包含认证秘密。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManagedAccountProfileView {
    private Long accountId;
    private Long subjectId;
    private String displayName;
    private String phone;
    private String email;
    private FileSignatureRefView avatar;
    private Long subjectVersion;
    private Long profileVersion;

    public static @NotNull ManagedAccountProfileView from(@NotNull ManagedAccountProfile profile) {
        FileSignatureRefView avatar = profile.getProfile().getAvatarFileId() == null
                ? null
                : new FileSignatureRefView()
                        .setScene(DefaultProfileAvatar.FILE_SCENE)
                        .setResourceId(profile.getSubjectId().toString())
                        .setFileId(profile.getProfile().getAvatarFileId());
        return new ManagedAccountProfileView(
                profile.getAccountId(),
                profile.getSubjectId(),
                profile.getDisplayName(),
                profile.getPhone(),
                profile.getEmail(),
                avatar,
                profile.getSubjectVersion(),
                profile.getProfile().getVersion());
    }
}
