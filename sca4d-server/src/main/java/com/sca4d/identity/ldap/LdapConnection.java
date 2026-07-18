package com.sca4d.identity.ldap;

import com.sca4d.common.security.EncryptedSecret;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** LDAP 认证使用的连接、搜索、属性映射和可选加密服务账号密码。 */
@Getter
@AllArgsConstructor
public class LdapConnection {

    private final Long providerId;
    private final String url;
    private final String baseDn;
    private final String serviceAccountDn;
    private final EncryptedSecret encryptedServiceAccountPassword;
    private final String userSearchBase;
    private final String userSearchFilter;
    private final String externalSubjectAttribute;
    private final String displayNameAttribute;
    private final String emailAttribute;
    private final LdapTransportSecurity transportSecurity;
    private final Long providerVersion;
}
