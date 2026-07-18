package com.sca4d.identity.oidc.view;

import com.sca4d.identity.oidc.OidcAvailableProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 登录页显示的 OIDC Provider 编码和名称。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OidcAvailableProviderView {

    private String code;
    private String name;

    /** @return OIDC Provider 的公开登录选项 */
    public static @NotNull OidcAvailableProviderView from(@NotNull OidcAvailableProvider provider) {
        return new OidcAvailableProviderView(provider.getCode(), provider.getName());
    }
}
