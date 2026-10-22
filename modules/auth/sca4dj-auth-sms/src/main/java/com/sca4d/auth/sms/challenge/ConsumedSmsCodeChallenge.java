package com.sca4d.auth.sms.challenge;

import com.sca4d.auth.sms.SmsCodeChallengeId;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存成功消费 SMS Challenge 后确认的 Account、引用和版本。 */
public class ConsumedSmsCodeChallenge {
    private final @NotNull Account account;

    private final @NotNull SmsCodeChallengeId challengeId;

    private final @NotNull Version version;

    /**
     * @param challengeId 已消费的 Challenge 标识
     * @param account 由 Challenge 确认的 Account
     * @param version 消费后用于并发校验的版本
     */
    public ConsumedSmsCodeChallenge(
            @NotNull SmsCodeChallengeId challengeId,
            @NotNull Account account,
            @NotNull Version version) {
        this.challengeId = Objects.requireNonNull(challengeId, "Challenge 标识不能为空");
        this.account = Objects.requireNonNull(account, "Challenge Account 不能为空");
        this.version = Objects.requireNonNull(version, "Challenge 版本不能为空");
    }

    /** @return 已消费的 Challenge 标识 */
    public @NotNull SmsCodeChallengeId challengeId() {
        return challengeId;
    }

    /** @return 由 Challenge 确认的 Account */
    public @NotNull Account account() {
        return account;
    }

    /** @return 消费后用于并发校验的版本 */
    public @NotNull Version version() {
        return version;
    }
}
