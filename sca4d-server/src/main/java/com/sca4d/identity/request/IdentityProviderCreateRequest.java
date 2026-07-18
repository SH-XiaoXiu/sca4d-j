package com.sca4d.identity.request;

import com.sca4d.common.validation.Required;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建默认停用 IdentityProvider 的通用字段请求。 */
@Data
public class IdentityProviderCreateRequest {

    @NotBlank
    @Pattern(regexp = "[a-z](?:[a-z0-9-]{0,62}[a-z0-9])?")
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    @Required
    private IdentityProviderProtocol protocol;

    @Required
    private IdentityProviderRegistrationPolicy registrationPolicy;

    private boolean emailAutoLink;
}
