package com.sca4d.identity.external;

import com.sca4d.identity.IdentityProviderScope;
import org.jetbrains.annotations.NotNull;

/** 使用已验证的上游资料创建本地用户、Account、Principal 和 ExternalIdentity。 */
public interface ExternalIdentityRegistrationService {

    /**
     * 按 Provider 作用域创建本地用户并绑定 external subject。
     *
     * <p>GLOBAL 创建 GLOBAL Account；TENANT 创建同租户 Account、Membership 和 TENANT Principal。
     *
     * @return 新建 Account 对应的 ExternalIdentity 验证结果
     */
    @NotNull
    ExternalIdentityVerification register(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull ExternalIdentityProfile profile);
}
