package com.sca4d.identity.external.view;

import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderProtocol;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 当前 Account 可以关联的 ACTIVE IdentityProvider，不包含内部主键和管理配置。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableIdentityProviderView {

    private String code;
    private String name;
    private IdentityProviderProtocol protocol;

    /** @return 账号安全页可以展示并提交的 Provider 编码、名称和协议 */
    public static @NotNull AvailableIdentityProviderView from(@NotNull IdentityProvider provider) {
        return new AvailableIdentityProviderView(
                provider.getCode(), provider.getName(), provider.getProtocol());
    }
}
