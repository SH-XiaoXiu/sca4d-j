package com.sca4d.auth.totp.config;

import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingCodec;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.SystemSetting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 声明 TOTP enrollment 可以由客户管理的有效期。 */
public final class TotpSettings {

    /** TOTP enrollment 有效期。 */
    public static final @NotNull SystemSetting<Duration> ENROLLMENT_TTL = new SystemSetting<>(
            "auth.totp.enrollment-ttl",
            1,
            new SettingGroup("authentication", "setting.group.authentication", 100),
            "setting.auth.totp.enrollment-ttl",
            "setting.auth.totp.enrollment-ttl.description",
            "SCA4DJ_AUTH_TOTP_ENROLLMENT_TTL",
            Duration.ofMinutes(10),
            SettingCodec.positiveDuration(),
            Pipeline.identity(Duration.class),
            false,
            400);

    private TotpSettings() {
    }
}
