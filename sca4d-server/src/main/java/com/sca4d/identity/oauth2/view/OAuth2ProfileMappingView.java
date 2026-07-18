package com.sca4d.identity.oauth2.view;

import com.sca4d.identity.oauth2.OAuth2ProfileMapping;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 管理端可读取的 OAuth2 Profile JSONPath 映射。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ProfileMappingView {

    private String subjectPath;
    private String displayNamePath;
    private String emailPath;
    private String emailVerifiedPath;
    private String avatarPath;

    /** @return Profile 映射的管理端响应 */
    public static @NotNull OAuth2ProfileMappingView from(@NotNull OAuth2ProfileMapping mapping) {
        return new OAuth2ProfileMappingView(
                mapping.getSubjectPath(),
                mapping.getDisplayNamePath(),
                mapping.getEmailPath(),
                mapping.getEmailVerifiedPath(),
                mapping.getAvatarPath());
    }
}
