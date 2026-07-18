package com.sca4d.identity.oauth2;

import com.sca4d.identity.external.ExternalIdentityProfile;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** OAuth2 主 Profile 和辅助 Profile 的映射结果及缺失字段。 */
@Getter
@AllArgsConstructor
public class OAuth2ProfilePreview {

    private final ExternalIdentityProfile profile;
    private final List<OAuth2ProfileField> missingFields;
}
