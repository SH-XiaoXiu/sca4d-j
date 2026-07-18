package com.sca4d.identity.oauth2.view;

import com.sca4d.identity.oauth2.OAuth2ProviderConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 不包含 Client Secret 明文或密文的 OAuth2 Provider 配置响应。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ProviderConfigurationView {

    private Long providerId;
    private String authorizationUri;
    private String tokenUri;
    private String profileUri;
    private String auxiliaryProfileUri;
    private String clientId;
    private Boolean clientSecretConfigured;
    private List<String> scopes;
    private OAuth2ProfileMappingView profileMapping;
    private Long version;

    /** @return 管理端可读取的 OAuth2 配置和父 Provider 版本 */
    public static @NotNull OAuth2ProviderConfigurationView from(
            @NotNull OAuth2ProviderConfiguration configuration) {
        return new OAuth2ProviderConfigurationView(
                configuration.getProviderId(),
                configuration.getAuthorizationUri(),
                configuration.getTokenUri(),
                configuration.getProfileUri(),
                configuration.getAuxiliaryProfileUri(),
                configuration.getClientId(),
                configuration.getClientSecretConfigured(),
                configuration.getScopes(),
                OAuth2ProfileMappingView.from(configuration.getProfileMapping()),
                configuration.getVersion());
    }
}
