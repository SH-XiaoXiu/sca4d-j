package com.sca4d.auth.sms.challenge;

import com.sca4d.auth.sms.SmsCodeChallengeId;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存成功消费 SMS Challenge 后确认的 Account、引用和版本。 */
public final class ConsumedSmsCodeChallenge {
    private final @NotNull SmsCodeChallengeId challengeId;

    private final @NotNull Account account;

    private final @NotNull Version version;

    /** 创建成功消费结果。 */
    public ConsumedSmsCodeChallenge(
            @NotNull SmsCodeChallengeId challengeId,
            @NotNull Account account,
            @NotNull Version version) {
        this.challengeId = Objects.requireNonNull(challengeId, "Challenge 标识不能为空");
        this.account = Objects.requireNonNull(account, "Challenge Account 不能为空");
        this.version = Objects.requireNonNull(version, "Challenge 版本不能为空");
    }

    public @NotNull SmsCodeChallengeId challengeId() {
        return challengeId;
    }

    public @NotNull Account account() {
        return account;
    }

    public @NotNull Version version() {
        return version;
    }
}
