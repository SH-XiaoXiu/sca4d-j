package com.sca4d.auth.federation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** 保存并单次消费 Redis 外部授权状态和回调结果。 */
public interface FederatedAuthorizationStore {

    /** 保存授权状态。 */
    void save(@NotNull FederatedAuthorization authorization, @NotNull Duration ttl);

    /** 按浏览器绑定消费授权状态。 */
    @Nullable
    FederatedAuthorization consume(@NotNull String id, @NotNull String browserBinding);

    /** 保存已完成且等待认证引擎消费的资料。 */
    void saveCompletion(
            @NotNull String id,
            @NotNull String providerCode,
            @NotNull FederatedAuthorizationPurpose purpose,
            @NotNull FederatedProfile profile,
            @NotNull String browserBinding,
            @NotNull Duration ttl);

    /** 消费已完成的资料。 */
    @Nullable
    FederatedCompletedAuthorization consumeCompletion(
            @NotNull String id,
            @NotNull String browserBinding,
            @NotNull FederatedAuthorizationPurpose purpose);
}
