package com.sca4d.identity.external;

import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.identity.IdentityProvider;
import org.jetbrains.annotations.NotNull;

/** 按认证目的处理上游身份，并返回当前可登录的账号。 */
public interface ExternalIdentityAuthenticationService {

    /**
     * 使用上游认证返回的资料处理现有绑定、可信邮箱关联、JIT 注册或当前 Account 绑定。
     *
     * @param authentication 当前浏览器中等待外部身份验证的认证
     * @param provider 当前作用域中已经启用的 IdentityProvider
     * @param profile 协议实现验证后得到的上游主体和资料
     * @return 当前仍可认证的 ExternalIdentity、Subject 和 Account
     */
    @NotNull
    ExternalIdentityVerification authenticate(
            @NotNull AuthenticationProcess authentication,
            @NotNull IdentityProvider provider,
            @NotNull ExternalIdentityProfile profile);
}
