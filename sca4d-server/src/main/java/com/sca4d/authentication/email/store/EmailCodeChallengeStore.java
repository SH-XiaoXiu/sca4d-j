package com.sca4d.authentication.email.store;

import com.sca4d.authentication.email.EmailCodeChallenge;
import com.sca4d.authentication.email.EmailCodeChallengeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** 邮件验证码 Challenge 的 Redis 创建、读取和原子状态推进契约。 */
public interface EmailCodeChallengeStore {

    boolean create(
            @NotNull EmailCodeChallenge challenge,
            @NotNull Duration ttl,
            @NotNull Duration resendInterval);

    @Nullable
    EmailCodeChallenge find(@NotNull String authenticationId, @NotNull String challengeId);

    boolean advance(
            @NotNull EmailCodeChallenge challenge,
            long expectedVersion,
            @NotNull EmailCodeChallengeState expectedState,
            boolean terminal);

    void cancel(@NotNull String authenticationId, @NotNull String challengeId);
}
