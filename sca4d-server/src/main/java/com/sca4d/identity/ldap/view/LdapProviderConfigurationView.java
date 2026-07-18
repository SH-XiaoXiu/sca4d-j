package com.sca4d.identity.ldap.view;

import com.sca4d.identity.ldap.LdapProviderConfiguration;
import com.sca4d.identity.ldap.LdapTransportSecurity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 不包含服务账号密码明文或密文的 LDAP Provider 配置响应。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LdapProviderConfigurationView {

    private Long providerId;
    private String url;
    private String baseDn;
    private String serviceAccountDn;
    private Boolean serviceAccountPasswordConfigured;
    private String userSearchBase;
    private String userSearchFilter;
    private String externalSubjectAttribute;
    private String displayNameAttribute;
    private String emailAttribute;
    private LdapTransportSecurity transportSecurity;
    private Long version;

    /** @return 管理端可读取的 LDAP 配置和父 Provider 版本 */
    public static @NotNull LdapProviderConfigurationView from(
            @NotNull LdapProviderConfiguration configuration) {
        return new LdapProviderConfigurationView(
                configuration.getProviderId(),
                configuration.getUrl(),
                configuration.getBaseDn(),
                configuration.getServiceAccountDn(),
                configuration.getServiceAccountPasswordConfigured(),
                configuration.getUserSearchBase(),
                configuration.getUserSearchFilter(),
                configuration.getExternalSubjectAttribute(),
                configuration.getDisplayNameAttribute(),
                configuration.getEmailAttribute(),
                configuration.getTransportSecurity(),
                configuration.getVersion());
    }
}
