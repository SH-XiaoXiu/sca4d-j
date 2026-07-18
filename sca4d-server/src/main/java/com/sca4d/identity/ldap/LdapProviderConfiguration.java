package com.sca4d.identity.ldap;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 管理端可读取且不包含服务账号密码密文的 LDAP Provider 配置。 */
@Getter
@AllArgsConstructor
public class LdapProviderConfiguration {

    private final Long providerId;
    private final String url;
    private final String baseDn;
    private final String serviceAccountDn;
    private final Boolean serviceAccountPasswordConfigured;
    private final String userSearchBase;
    private final String userSearchFilter;
    private final String externalSubjectAttribute;
    private final String displayNameAttribute;
    private final String emailAttribute;
    private final LdapTransportSecurity transportSecurity;
    private final Long version;
}
