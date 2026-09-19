package com.sca4d.auth.passkey.config;

import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingCodec;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.SystemSetting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 声明 Passkey ceremony 有效期和账号 Credential 数量上限。 */
public final class PasskeySettings {

    private static final SettingGroup GROUP = new SettingGroup("authentication", "setting.group.authentication", 100);

    /** registration 和 assertion ceremony 有效期。 */
    public static final @NotNull SystemSetting<Duration> CEREMONY_TTL = new SystemSetting<>(
            "auth.passkey.ceremony-ttl",
            1,
            GROUP,
            "setting.auth.passkey.ceremony-ttl",
            "setting.auth.passkey.ceremony-ttl.description",
            "SCA4DJ_AUTH_PASSKEY_CEREMONY_TTL",
            Duration.ofMinutes(5),
            SettingCodec.positiveDuration(),
            Pipeline.identity(Duration.class),
            false,
            500);

    /** 每个账号允许保存的 Passkey Credential 上限。 */
    public static final @NotNull SystemSetting<Integer> MAXIMUM_CREDENTIALS = new SystemSetting<>(
            "auth.passkey.maximum-credentials",
            1,
            GROUP,
            "setting.auth.passkey.maximum-credentials",
            "setting.auth.passkey.maximum-credentials.description",
            "SCA4DJ_AUTH_PASSKEY_MAXIMUM_CREDENTIALS",
            5,
            SettingCodec.positiveInteger(),
            Pipeline.identity(Integer.class),
            false,
            600);

    private PasskeySettings() {
    }
}
