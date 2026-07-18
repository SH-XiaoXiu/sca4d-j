package com.sca4d.identity.request;

import com.sca4d.common.validation.Required;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改 IdentityProvider 显示名称、注册策略和邮箱关联开关的请求。 */
@Data
public class IdentityProviderUpdateRequest {

    @NotBlank
    @Size(max = 128)
    private String name;

    @Required
    private IdentityProviderRegistrationPolicy registrationPolicy;

    private boolean emailAutoLink;
}
