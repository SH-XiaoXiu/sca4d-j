package com.sca4d.identity.ldap.request;

import com.sca4d.common.validation.Required;
import com.sca4d.identity.ldap.LdapTransportSecurity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建或完整替换 LDAP Provider 配置的请求。 */
@Data
public class LdapProviderConfigurationRequest {

    @NotBlank
    @Size(max = 1024)
    private String url;

    @NotBlank
    @Size(max = 512)
    private String baseDn;

    @Size(max = 512)
    private String serviceAccountDn;

    @Size(max = 4096)
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
    private String serviceAccountPassword;

    @Required
    @Size(max = 512)
    private String userSearchBase;

    @NotBlank
    @Size(max = 512)
    private String userSearchFilter;

    @NotBlank
    @Size(max = 128)
    private String externalSubjectAttribute;

    @Size(max = 128)
    private String displayNameAttribute;

    @Size(max = 128)
    private String emailAttribute;

    @Required
    private LdapTransportSecurity transportSecurity;
}
