package com.sca4d.identity.oauth2;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 登录页可以选择的 ACTIVE OAuth2 Provider 编码和显示名称。 */
@Getter
@AllArgsConstructor
public class OAuth2AvailableProvider {

    private final String code;
    private final String name;
}
