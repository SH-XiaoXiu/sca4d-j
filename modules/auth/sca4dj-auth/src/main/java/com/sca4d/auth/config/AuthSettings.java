package com.sca4d.auth.config;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import com.sca4d.core.api.setting.definition.SystemSetting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 声明认证模块可以由客户管理的会话、尝试和 JWT 有效期。 */
public class AuthSettings {

    /** 认证设置分组。 */
    public static final @NotNull SettingGroup GROUP = new SettingGroup(
            TreeCode.of("AUTHENTICATION"),
            "setting.group.authentication",
            100);

    /** 登录建立的 Session 有效期。 */
    public final @NotNull SystemSetting<Duration> SESSION_TTL;

    /** 跨请求认证尝试有效期。 */
    public final @NotNull SystemSetting<Duration> ATTEMPT_TTL;

    /** 短效 access JWT 有效期。 */
    public final @NotNull SystemSetting<Duration> JWT_ACCESS_TTL;

    /** 构造模块设置，每个应用上下文独立保存 Source。 */
    public AuthSettings(@NotNull SettingReader<String> reader, SettingWriter<String> writer) {
        this.SESSION_TTL = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("AUTH:SESSION_TTL"),
                GROUP,
                "setting.auth.session-ttl",
                "setting.auth.session-ttl.description",
                "AUTH_SESSION_TTL",
                Duration.ofDays(30),
                SettingCodec.positiveDuration(),
                Pipeline.identity(Duration.class),
                false,
                100);
        this.ATTEMPT_TTL = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("AUTH:ATTEMPT_TTL"),
                GROUP,
                "setting.auth.attempt-ttl",
                "setting.auth.attempt-ttl.description",
                "AUTH_ENGINE_ATTEMPT_TTL",
                Duration.ofMinutes(5),
                SettingCodec.positiveDuration(),
                Pipeline.identity(Duration.class),
                false,
                200);
        this.JWT_ACCESS_TTL = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("AUTH:JWT:ACCESS_TTL"),
                GROUP,
                "setting.auth.jwt.access-ttl",
                "setting.auth.jwt.access-ttl.description",
                "AUTH_JWT_ACCESS_TTL",
                Duration.ofMinutes(5),
                SettingCodec.positiveDuration(),
                Pipeline.identity(Duration.class),
                false,
                300);
    }
}
