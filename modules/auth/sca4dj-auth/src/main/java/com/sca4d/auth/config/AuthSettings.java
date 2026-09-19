package com.sca4d.auth.config;

import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingCodec;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.SystemSetting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 声明认证模块可以由客户管理的会话、尝试和 JWT 有效期。 */
public final class AuthSettings {

    /** 认证设置分组。 */
    public static final @NotNull SettingGroup GROUP = new SettingGroup(
            "authentication",
            "setting.group.authentication",
            100);

    /** 登录建立的 Session 有效期。 */
    public static final @NotNull SystemSetting<Duration> SESSION_TTL = new SystemSetting<>(
            "auth.session-ttl",
            1,
            GROUP,
            "setting.auth.session-ttl",
            "setting.auth.session-ttl.description",
            "SCA4DJ_AUTH_SESSION_TTL",
            Duration.ofDays(30),
            SettingCodec.positiveDuration(),
            Pipeline.identity(Duration.class),
            false,
            100);

    /** 跨请求认证尝试有效期。 */
    public static final @NotNull SystemSetting<Duration> ATTEMPT_TTL = new SystemSetting<>(
            "auth.attempt-ttl",
            1,
            GROUP,
            "setting.auth.attempt-ttl",
            "setting.auth.attempt-ttl.description",
            "SCA4DJ_AUTH_ENGINE_ATTEMPT_TTL",
            Duration.ofMinutes(5),
            SettingCodec.positiveDuration(),
            Pipeline.identity(Duration.class),
            false,
            200);

    /** 短效 access JWT 有效期。 */
    public static final @NotNull SystemSetting<Duration> JWT_ACCESS_TTL = new SystemSetting<>(
            "auth.jwt.access-ttl",
            1,
            GROUP,
            "setting.auth.jwt.access-ttl",
            "setting.auth.jwt.access-ttl.description",
            "SCA4DJ_AUTH_JWT_ACCESS_TTL",
            Duration.ofMinutes(5),
            SettingCodec.positiveDuration(),
            Pipeline.identity(Duration.class),
            false,
            300);

    private AuthSettings() {
    }
}
