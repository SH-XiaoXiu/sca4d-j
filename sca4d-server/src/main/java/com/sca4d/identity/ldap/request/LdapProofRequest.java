package com.sca4d.identity.ldap.request;

import com.sca4d.common.validation.Required;
import com.sca4d.common.validation.SafeText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 向 Authentication 提交 LDAP Provider、目录账号和短期密码 Proof。 */
@Data
@NoArgsConstructor
public class LdapProofRequest {

    @Required
    @SafeText(max = 64)
    private String providerCode;

    @NotBlank
    @Size(max = 256)
    private String username;

    @NotBlank
    @Size(max = 4096)
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
    private String password;
}
