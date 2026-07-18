package com.sca4d.identity.oidc.view;

import com.sca4d.identity.oidc.OidcProviderConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 不包含 Client Secret 明文或密文的 OIDC Provider 配置响应。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OidcProviderConfigurationView {

    private Long providerId;
    private String issuer;
    private String clientId;
    private Boolean clientSecretConfigured;
    private List<String> scopes;
    private Long version;

    /** @return 管理端可读取的 OIDC 配置和父 Provider 版本 */
    public static @NotNull OidcProviderConfigurationView from(
            @NotNull OidcProviderConfiguration configuration) {
        return new OidcProviderConfigurationView(
                configuration.getProviderId(),
                configuration.getIssuer(),
                configuration.getClientId(),
                configuration.getClientSecretConfigured(),
                configuration.getScopes(),
                configuration.getVersion());
    }
}
