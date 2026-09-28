package com.sca4d.auth.totp.config;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import com.sca4d.core.api.setting.definition.SystemSetting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 声明 TOTP enrollment 可以由客户管理的有效期。 */
public class TotpSettings {

    /** TOTP enrollment 有效期。 */
    public final @NotNull SystemSetting<Duration> ENROLLMENT_TTL;

    /** 构造模块设置，每个应用上下文独立保存 Source。 */
    public TotpSettings(@NotNull SettingReader<String> reader, SettingWriter<String> writer) {
        this.ENROLLMENT_TTL = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("AUTH:TOTP:ENROLLMENT_TTL"),
                new SettingGroup(TreeCode.of("AUTHENTICATION"), "setting.group.authentication", 100),
                "setting.auth.totp.enrollment-ttl",
                "setting.auth.totp.enrollment-ttl.description",
                "AUTH_TOTP_ENROLLMENT_TTL",
                Duration.ofMinutes(10),
                SettingCodec.positiveDuration(),
                Pipeline.identity(Duration.class),
                false,
                400);
    }
}
