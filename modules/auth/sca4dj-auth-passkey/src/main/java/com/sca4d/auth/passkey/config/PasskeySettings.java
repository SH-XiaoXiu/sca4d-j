package com.sca4d.auth.passkey.config;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import com.sca4d.core.api.setting.definition.SystemSetting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 声明 Passkey ceremony 有效期和账号 Credential 数量上限。 */
public final class PasskeySettings {

    private static final SettingGroup GROUP = new SettingGroup(
            TreeCode.of("AUTHENTICATION"),
            "setting.group.authentication",
            100);

    /** registration 和 assertion ceremony 有效期。 */
    public final @NotNull SystemSetting<Duration> CEREMONY_TTL;

    /** 每个账号允许保存的 Passkey Credential 上限。 */
    public final @NotNull SystemSetting<Integer> MAXIMUM_CREDENTIALS;

    /** 构造模块设置，每个应用上下文独立保存 Source。 */
    public PasskeySettings(@NotNull SettingReader<String> reader, SettingWriter<String> writer) {
        this.CEREMONY_TTL = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("AUTH:PASSKEY:CEREMONY_TTL"),
                GROUP,
                "setting.auth.passkey.ceremony-ttl",
                "setting.auth.passkey.ceremony-ttl.description",
                "SCA4DJ_AUTH_PASSKEY_CEREMONY_TTL",
                Duration.ofMinutes(5),
                SettingCodec.positiveDuration(),
                Pipeline.identity(Duration.class),
                false,
                500);
        this.MAXIMUM_CREDENTIALS = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("AUTH:PASSKEY:MAXIMUM_CREDENTIALS"),
                GROUP,
                "setting.auth.passkey.maximum-credentials",
                "setting.auth.passkey.maximum-credentials.description",
                "SCA4DJ_AUTH_PASSKEY_MAXIMUM_CREDENTIALS",
                5,
                SettingCodec.positiveInteger(),
                Pipeline.identity(Integer.class),
                false,
                600);
    }
}
