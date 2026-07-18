package com.sca4d.identity.ldap;

import com.sca4d.identity.external.ExternalIdentityProfile;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** LDAP 搜索得到的唯一用户 DN 和标准身份资料。 */
@Getter
@AllArgsConstructor
public class LdapDirectoryIdentity {

    private final String absoluteDn;
    private final ExternalIdentityProfile profile;
}
