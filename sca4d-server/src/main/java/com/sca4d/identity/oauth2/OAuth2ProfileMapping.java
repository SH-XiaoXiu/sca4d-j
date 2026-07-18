package com.sca4d.identity.oauth2;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 从 OAuth2 Profile JSON 读取通用身份资料的只读 JSONPath 集合。 */
@Getter
@AllArgsConstructor
public class OAuth2ProfileMapping {

    private final String subjectPath;
    private final String displayNamePath;
    private final String emailPath;
    private final String emailVerifiedPath;
    private final String avatarPath;
}
