package com.sca4d.identity.oidc.request;

import com.sca4d.common.validation.Required;
import com.sca4d.common.validation.SafeText;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 为 Authentication 选择一个 OIDC Provider 的请求。 */
@Data
@NoArgsConstructor
public class OidcAuthorizationCreateRequest {

    @Required
    @SafeText(max = 64)
    private String providerCode;
}
