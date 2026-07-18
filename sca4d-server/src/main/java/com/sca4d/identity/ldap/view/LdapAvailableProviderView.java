package com.sca4d.identity.ldap.view;

import com.sca4d.identity.ldap.LdapAvailableProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 登录页显示的 LDAP Provider 编码和名称。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LdapAvailableProviderView {

    private String code;
    private String name;

    public static @NotNull LdapAvailableProviderView from(
            @NotNull LdapAvailableProvider provider) {
        return new LdapAvailableProviderView(provider.getCode(), provider.getName());
    }
}
