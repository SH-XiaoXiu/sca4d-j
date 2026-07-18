package com.sca4d.identity.ldap;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 登录页可以选择的 ACTIVE LDAP Provider 编码和显示名称。 */
@Getter
@AllArgsConstructor
public class LdapAvailableProvider {

    private final String code;
    private final String name;
}
