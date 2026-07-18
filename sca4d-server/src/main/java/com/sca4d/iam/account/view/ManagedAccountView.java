package com.sca4d.iam.account.view;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.file.view.FileSignatureRefView;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.account.ManagedAccount;
import com.sca4d.iam.profile.DefaultProfileAvatar;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/** 管理端 Account 分页记录。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManagedAccountView {
    private Long accountId;
    private Long subjectId;
    private Long defaultPrincipalId;
    private String displayName;
    private String primaryIdentifier;
    private FileSignatureRefView avatar;
    private Long subjectVersion;
    private Long profileVersion;
    private AccountScopeType scopeType;
    private Long scopeId;
    private AccountStatus status;
    private boolean secondFactorRequired;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** 将 Account 读取对象转换为 HTTP 响应。 */
    public static @NotNull ManagedAccountView from(@NotNull ManagedAccount account) {
        FileSignatureRefView avatar = account.getAvatarFileId() == null
                ? null
                : new FileSignatureRefView()
                        .setScene(DefaultProfileAvatar.FILE_SCENE)
                        .setResourceId(account.getSubjectId().toString())
                        .setFileId(account.getAvatarFileId());
        return new ManagedAccountView(
                account.getAccountId(),
                account.getSubjectId(),
                account.getDefaultPrincipalId(),
                account.getDisplayName(),
                account.getPrimaryIdentifier(),
                avatar,
                account.getSubjectVersion(),
                account.getProfileVersion(),
                account.getScopeType(),
                account.getScopeId(),
                account.getStatus(),
                account.isSecondFactorRequired(),
                account.getVersion(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
