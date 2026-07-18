package com.sca4d.identity;

import org.jetbrains.annotations.NotNull;

/** 判断指定协议子表是否已经包含启用 IdentityProvider 所需的全部配置。 */
public interface IdentityProviderActivationCheck {

    /** @return 当前实现检查的协议 */
    @NotNull
    IdentityProviderProtocol protocol();

    /** @return 对应协议配置存在且字段组合可以发起认证时返回 true */
    boolean canActivate(@NotNull Long providerId);
}
