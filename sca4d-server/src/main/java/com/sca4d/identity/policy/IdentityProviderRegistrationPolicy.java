package com.sca4d.identity.policy;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 上游身份没有本地关联时是否允许创建 Subject 和 Account。 */
public enum IdentityProviderRegistrationPolicy implements BaseEnum {
    /** 未关联的 external subject 拒绝登录。 */
    DISABLED("DISABLED", "禁止注册"),
    /** 按 Provider 作用域创建 Subject、Account 和默认 Principal。 */
    JIT("JIT", "即时注册");

    private final String value;
    private final String description;

    IdentityProviderRegistrationPolicy(@NotNull String value, @NotNull String description) {
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
