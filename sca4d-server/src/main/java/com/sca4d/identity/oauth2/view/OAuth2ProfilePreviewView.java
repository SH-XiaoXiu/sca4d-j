package com.sca4d.identity.oauth2.view;

import com.sca4d.identity.external.ExternalIdentityProfile;
import com.sca4d.identity.oauth2.OAuth2ProfileField;
import com.sca4d.identity.oauth2.OAuth2ProfilePreview;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 管理端查看的 OAuth2 Profile 标准字段映射结果和缺失字段。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ProfilePreviewView {

    private String subject;
    private String displayName;
    private String email;
    private Boolean emailVerified;
    private String avatarUri;
    private List<OAuth2ProfileField> missingFields;

    public static @NotNull OAuth2ProfilePreviewView from(@NotNull OAuth2ProfilePreview preview) {
        ExternalIdentityProfile profile = preview.getProfile();
        return new OAuth2ProfilePreviewView(
                profile.getExternalSubject(),
                profile.getDisplayName(),
                profile.getEmail(),
                profile.getEmailVerified(),
                profile.getAvatarUri(),
                preview.getMissingFields());
    }
}
