package com.sca4d.identity.oauth2.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.common.validation.Required;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 使用示例主 Profile 和可选辅助 Profile 预览当前 OAuth2 JSONPath 映射。 */
@Data
@NoArgsConstructor
public class OAuth2ProfilePreviewRequest {

    @Required
    private JsonNode profile;

    private JsonNode auxiliaryProfile;
}
