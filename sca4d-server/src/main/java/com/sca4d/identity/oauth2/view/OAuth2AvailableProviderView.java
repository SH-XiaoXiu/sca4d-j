package com.sca4d.identity.oauth2.view;

import com.sca4d.identity.oauth2.OAuth2AvailableProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 登录页显示的 OAuth2 Provider 编码和名称。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2AvailableProviderView {

    private String code;
    private String name;

    public static @NotNull OAuth2AvailableProviderView from(
            @NotNull OAuth2AvailableProvider provider) {
        return new OAuth2AvailableProviderView(provider.getCode(), provider.getName());
    }
}
