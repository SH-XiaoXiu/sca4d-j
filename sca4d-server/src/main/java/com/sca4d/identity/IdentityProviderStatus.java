package com.sca4d.identity;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** IdentityProvider 是否出现在认证入口并允许创建 Proof。 */
public enum IdentityProviderStatus implements BaseEnum {
    /** 协议配置完整，可以用于登录、注册或关联。 */
    ACTIVE("ACTIVE", "启用"),
    /** 只能由管理员读取和修改，不能发起认证。 */
    DISABLED("DISABLED", "停用");

    private final String value;
    private final String description;

    IdentityProviderStatus(@NotNull String value, @NotNull String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
