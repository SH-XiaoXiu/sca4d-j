package com.sca4d.app.server.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.region.Region;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

/** 保存当前 Account 三项通知设置的领域化读取结果。 */
public final class NotificationSettingsSnapshot {
    private final Locale language;

    private final Region region;

    private final JsonNode channels;

    private final Version languageVersion;

    private final Version regionVersion;

    private final Version channelsVersion;

    public NotificationSettingsSnapshot(
            @NotNull Locale language,
            @NotNull Region region,
            @NotNull JsonNode channels,
            @NotNull Version languageVersion,
            @NotNull Version regionVersion,
            @NotNull Version channelsVersion) {
        this.language = Objects.requireNonNull(language, "语言设置不能为空");
        this.region = Objects.requireNonNull(region, "区域设置不能为空");
        this.channels = Objects.requireNonNull(channels, "渠道设置不能为空").deepCopy();
        this.languageVersion = Objects.requireNonNull(languageVersion, "语言设置版本不能为空");
        this.regionVersion = Objects.requireNonNull(regionVersion, "区域设置版本不能为空");
        this.channelsVersion = Objects.requireNonNull(channelsVersion, "渠道设置版本不能为空");
    }

    public Locale language() {
        return language;
    }

    public Region region() {
        return region;
    }

    public JsonNode channels() {
        return channels.deepCopy();
    }

    public Version languageVersion() {
        return languageVersion;
    }

    public Version regionVersion() {
        return regionVersion;
    }

    public Version channelsVersion() {
        return channelsVersion;
    }
}
