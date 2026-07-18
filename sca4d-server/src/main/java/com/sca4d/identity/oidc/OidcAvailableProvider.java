package com.sca4d.identity.oidc;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 登录页可以选择的 ACTIVE OIDC Provider 编码和显示名称。 */
@Getter
@AllArgsConstructor
public class OidcAvailableProvider {

    private final String code;
    private final String name;
}
