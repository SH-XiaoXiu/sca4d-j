package com.sca4d.auth.email.config;

import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingCodec;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.SystemSetting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 声明 Email Code Challenge 有效期、重发间隔和错误次数上限。 */
public final class EmailCodeSettings {

    private static final SettingGroup GROUP = new SettingGroup("authentication", "setting.group.authentication", 100);

    /** Challenge 和验证码有效期。 */
    public static final @NotNull SystemSetting<Duration> CHALLENGE_TTL = new SystemSetting<>(
            "auth.email-code.challenge-ttl",
            1,
            GROUP,
            "setting.auth.email-code.challenge-ttl",
            "setting.auth.email-code.challenge-ttl.description",
            "SCA4DJ_AUTH_EMAIL_CODE_CHALLENGE_TTL",
            Duration.ofMinutes(5),
            SettingCodec.positiveDuration(),
            Pipeline.identity(Duration.class),
            false,
            500);

    /** 同一邮箱或 Account 的最短重新签发间隔。 */
    public static final @NotNull SystemSetting<Duration> RESEND_INTERVAL = new SystemSetting<>(
            "auth.email-code.resend-interval",
            1,
            GROUP,
            "setting.auth.email-code.resend-interval",
            "setting.auth.email-code.resend-interval.description",
            "SCA4DJ_AUTH_EMAIL_CODE_RESEND_INTERVAL",
            Duration.ofSeconds(60),
            SettingCodec.positiveDuration(),
            Pipeline.identity(Duration.class),
            false,
            510);

    /** 单个 Challenge 允许的错误验证码次数。 */
    public static final @NotNull SystemSetting<Integer> MAXIMUM_ATTEMPTS = new SystemSetting<>(
            "auth.email-code.maximum-attempts",
            1,
            GROUP,
            "setting.auth.email-code.maximum-attempts",
            "setting.auth.email-code.maximum-attempts.description",
            "SCA4DJ_AUTH_EMAIL_CODE_MAXIMUM_ATTEMPTS",
            5,
            SettingCodec.positiveInteger(),
            Pipeline.identity(Integer.class),
            false,
            520);

    private EmailCodeSettings() {
    }
}
