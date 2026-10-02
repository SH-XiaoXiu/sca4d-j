package com.sca4d.app.server.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.region.Region;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

/** 保存当前 Account 三项通知设置的领域化读取结果。 */
public class NotificationSettingsSnapshot {
    private final JsonNode channels;

    private final Locale language;

    private final Region region;

    private final Version channelsVersion;

    private final Version languageVersion;

    private final Version regionVersion;

    /** 创建当前 Account 的通知设置快照。 */
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

    /** @return 当前语言 */
    public Locale language() {
        return language;
    }

    /** @return 当前业务区域 */
    public Region region() {
        return region;
    }

    /** @return 渠道配置副本 */
    public JsonNode channels() {
        return channels.deepCopy();
    }

    /** @return 语言设置版本 */
    public Version languageVersion() {
        return languageVersion;
    }

    /** @return 区域设置版本 */
    public Version regionVersion() {
        return regionVersion;
    }

    /** @return 渠道设置版本 */
    public Version channelsVersion() {
        return channelsVersion;
    }
}
