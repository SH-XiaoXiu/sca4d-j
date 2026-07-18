package com.sca4d.identity.oauth2.request;

import com.sca4d.common.validation.Required;
import com.sca4d.common.validation.SafeText;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 为 Authentication 选择一个 OAuth2 Provider 的请求。 */
@Data
@NoArgsConstructor
public class OAuth2AuthorizationCreateRequest {

    @Required
    @SafeText(max = 64)
    private String providerCode;
}
