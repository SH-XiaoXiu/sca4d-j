package com.sca4d.auth.sms.config;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import com.sca4d.core.api.setting.definition.SystemSetting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 声明 SMS Code Challenge 有效期、重发间隔和错误次数上限。 */
public class SmsCodeSettings {
    private static final SettingGroup GROUP = new SettingGroup(
            TreeCode.of("AUTHENTICATION"),
            "setting.group.authentication",
            100);

    public final @NotNull SystemSetting<Duration> CHALLENGE_TTL;

    public final @NotNull SystemSetting<Duration> RESEND_INTERVAL;

    public final @NotNull SystemSetting<Integer> MAXIMUM_ATTEMPTS;

    /** 构造模块设置，每个应用上下文独立保存 Source。 */
    public SmsCodeSettings(@NotNull SettingReader<String> reader, SettingWriter<String> writer) {
        this.CHALLENGE_TTL = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("AUTH:SMS_CODE:CHALLENGE_TTL"),
                GROUP,
                "setting.auth.sms-code.challenge-ttl",
                "setting.auth.sms-code.challenge-ttl.description",
                "AUTH_SMS_CODE_CHALLENGE_TTL",
                Duration.ofMinutes(5),
                SettingCodec.positiveDuration(),
                Pipeline.identity(Duration.class),
                false,
                530);
        this.RESEND_INTERVAL = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("AUTH:SMS_CODE:RESEND_INTERVAL"),
                GROUP,
                "setting.auth.sms-code.resend-interval",
                "setting.auth.sms-code.resend-interval.description",
                "AUTH_SMS_CODE_RESEND_INTERVAL",
                Duration.ofSeconds(60),
                SettingCodec.positiveDuration(),
                Pipeline.identity(Duration.class),
                false,
                540);
        this.MAXIMUM_ATTEMPTS = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("AUTH:SMS_CODE:MAXIMUM_ATTEMPTS"),
                GROUP,
                "setting.auth.sms-code.maximum-attempts",
                "setting.auth.sms-code.maximum-attempts.description",
                "AUTH_SMS_CODE_MAXIMUM_ATTEMPTS",
                5,
                SettingCodec.positiveInteger(),
                Pipeline.identity(Integer.class),
                false,
                550);
    }
}
