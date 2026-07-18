package com.sca4d.identity.external;

import com.sca4d.identity.IdentityProviderScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 认证、创建和复核 IdentityProvider external subject 与本地 Account 的绑定。 */
public interface ExternalIdentityService {

    /**
     * 记录一次上游认证，并在 external subject 已绑定时返回当前本地 Account。
     *
     * @return 当前可认证绑定；尚未绑定时返回 null
     */
    @Nullable
    ExternalIdentityVerification authenticate(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull ExternalIdentityProfile profile);

    /**
     * 把上游 external subject 绑定到同一 Provider 作用域中的可认证 Account。
     *
     * @return 新绑定或同一 Account 已有绑定的当前验证结果
     */
    @NotNull
    ExternalIdentityVerification link(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull Long accountId,
            @NotNull ExternalIdentityProfile profile);

    /** @return Session 创建前仍可认证且绑定版本未变化的 Subject、Account 和 ExternalIdentity */
    @NotNull
    ExternalIdentityVerification requireCurrent(
            @NotNull Long identityId, @NotNull Long identityVersion);
}
