package com.sca4d.auth.email.config;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import com.sca4d.core.api.setting.definition.SystemSetting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 声明 Email Code Challenge 有效期、重发间隔和错误次数上限。 */
public final class EmailCodeSettings {

    private static final SettingGroup GROUP = new SettingGroup(
            TreeCode.of("AUTHENTICATION"),
            "setting.group.authentication",
            100);

    /** Challenge 和验证码有效期。 */
    public final @NotNull SystemSetting<Duration> CHALLENGE_TTL;

    /** 同一邮箱或 Account 的最短重新签发间隔。 */
    public final @NotNull SystemSetting<Duration> RESEND_INTERVAL;

    /** 单个 Challenge 允许的错误验证码次数。 */
    public final @NotNull SystemSetting<Integer> MAXIMUM_ATTEMPTS;

    /** 构造模块设置，每个应用上下文独立保存 Source。 */
    public EmailCodeSettings(@NotNull SettingReader<String> reader, SettingWriter<String> writer) {
        this.CHALLENGE_TTL = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("AUTH:EMAIL_CODE:CHALLENGE_TTL"),
                GROUP,
                "setting.auth.email-code.challenge-ttl",
                "setting.auth.email-code.challenge-ttl.description",
                "SCA4DJ_AUTH_EMAIL_CODE_CHALLENGE_TTL",
                Duration.ofMinutes(5),
                SettingCodec.positiveDuration(),
                Pipeline.identity(Duration.class),
                false,
                500);
        this.RESEND_INTERVAL = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("AUTH:EMAIL_CODE:RESEND_INTERVAL"),
                GROUP,
                "setting.auth.email-code.resend-interval",
                "setting.auth.email-code.resend-interval.description",
                "SCA4DJ_AUTH_EMAIL_CODE_RESEND_INTERVAL",
                Duration.ofSeconds(60),
                SettingCodec.positiveDuration(),
                Pipeline.identity(Duration.class),
                false,
                510);
        this.MAXIMUM_ATTEMPTS = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("AUTH:EMAIL_CODE:MAXIMUM_ATTEMPTS"),
                GROUP,
                "setting.auth.email-code.maximum-attempts",
                "setting.auth.email-code.maximum-attempts.description",
                "SCA4DJ_AUTH_EMAIL_CODE_MAXIMUM_ATTEMPTS",
                5,
                SettingCodec.positiveInteger(),
                Pipeline.identity(Integer.class),
                false,
                520);
    }
}
