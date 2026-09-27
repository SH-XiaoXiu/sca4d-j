package com.sca4d.auth.email.challenge;

import com.sca4d.auth.email.EmailCodeChallengeId;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存成功消费 Challenge 后确认的 Account、非秘密引用和版本。 */
public class ConsumedEmailCodeChallenge {

    private final @NotNull EmailCodeChallengeId challengeId;

    private final @NotNull Account account;

    private final @NotNull Version version;

    /** @param challengeId 已消费 Challenge @param account 已确认 Account @param version 消费后的版本 */
    public ConsumedEmailCodeChallenge(
            @NotNull EmailCodeChallengeId challengeId,
            @NotNull Account account,
            @NotNull Version version) {
        this.challengeId = Objects.requireNonNull(challengeId, "Challenge 标识不能为空");
        this.account = Objects.requireNonNull(account, "Challenge Account 不能为空");
        this.version = Objects.requireNonNull(version, "Challenge 版本不能为空");
    }

    /** @return 已消费 Challenge */
    @Contract(pure = true)
    public @NotNull EmailCodeChallengeId challengeId() {
        return challengeId;
    }

    /** @return 已确认 Account */
    @Contract(pure = true)
    public @NotNull Account account() {
        return account;
    }

    /** @return 消费后的版本 */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }
}
