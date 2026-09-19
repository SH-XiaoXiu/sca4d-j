package com.sca4d.auth.sms.config;

import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingCodec;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.SystemSetting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 声明 SMS Code Challenge 有效期、重发间隔和错误次数上限。 */
public final class SmsCodeSettings {
    private static final SettingGroup GROUP = new SettingGroup("authentication", "setting.group.authentication", 100);

    public static final @NotNull SystemSetting<Duration> CHALLENGE_TTL = new SystemSetting<>(
            "auth.sms-code.challenge-ttl",
            1,
            GROUP,
            "setting.auth.sms-code.challenge-ttl",
            "setting.auth.sms-code.challenge-ttl.description",
            "SCA4DJ_AUTH_SMS_CODE_CHALLENGE_TTL",
            Duration.ofMinutes(5),
            SettingCodec.positiveDuration(),
            Pipeline.identity(Duration.class),
            false,
            530);

    public static final @NotNull SystemSetting<Duration> RESEND_INTERVAL = new SystemSetting<>(
            "auth.sms-code.resend-interval",
            1,
            GROUP,
            "setting.auth.sms-code.resend-interval",
            "setting.auth.sms-code.resend-interval.description",
            "SCA4DJ_AUTH_SMS_CODE_RESEND_INTERVAL",
            Duration.ofSeconds(60),
            SettingCodec.positiveDuration(),
            Pipeline.identity(Duration.class),
            false,
            540);

    public static final @NotNull SystemSetting<Integer> MAXIMUM_ATTEMPTS = new SystemSetting<>(
            "auth.sms-code.maximum-attempts",
            1,
            GROUP,
            "setting.auth.sms-code.maximum-attempts",
            "setting.auth.sms-code.maximum-attempts.description",
            "SCA4DJ_AUTH_SMS_CODE_MAXIMUM_ATTEMPTS",
            5,
            SettingCodec.positiveInteger(),
            Pipeline.identity(Integer.class),
            false,
            550);

    private SmsCodeSettings() {
    }
}
