package com.sca4d.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.sca4d.identity.ldap.LdapTransportSecurity;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** LDAP 地址、搜索、属性映射和加密服务账号密码的持久化实体。 */
@Data
@Accessors(chain = true)
@Table("iam_ldap_provider")
public class IamLdapProviderEntity {

    @Id
    private Long providerId;
    private String url;
    private String baseDn;
    private String serviceAccountDn;
    private String serviceAccountPasswordKeyId;
    private byte[] serviceAccountPasswordNonce;
    private byte[] serviceAccountPasswordCiphertext;
    private String userSearchBase;
    private String userSearchFilter;
    private String externalSubjectAttribute;
    private String displayNameAttribute;
    private String emailAttribute;
    private LdapTransportSecurity transportSecurity;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
