package com.sca4d.identity.oauth2;

import com.sca4d.common.security.EncryptedSecret;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** OAuth2 认证请求使用的端点、Client、Profile 映射、父 Provider 版本和加密 Client Secret。 */
@Getter
@AllArgsConstructor
public class OAuth2Client {

    private final Long providerId;
    private final String authorizationUri;
    private final String tokenUri;
    private final String profileUri;
    private final String auxiliaryProfileUri;
    private final String clientId;
    private final EncryptedSecret encryptedClientSecret;
    private final List<String> scopes;
    private final OAuth2ProfileMapping profileMapping;
    private final Long providerVersion;
}
