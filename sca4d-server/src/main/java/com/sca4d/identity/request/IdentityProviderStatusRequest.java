package com.sca4d.identity.request;

import com.sca4d.common.validation.Required;
import com.sca4d.identity.IdentityProviderStatus;
import lombok.Data;

/** 启用或停用 IdentityProvider 的请求。 */
@Data
public class IdentityProviderStatusRequest {

    @Required
    private IdentityProviderStatus status;
}
