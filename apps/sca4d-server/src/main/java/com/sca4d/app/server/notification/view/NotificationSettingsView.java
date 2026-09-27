package com.sca4d.app.server.notification.view;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sca4d.app.server.notification.NotificationSettingsSnapshot;
import com.sca4d.core.api.notification.NotificationType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

/** 返回当前 Account 的全局通知语言、区域和可选渠道覆盖值。 */
@SuppressWarnings("unused")
public final class NotificationSettingsView {
    private final String languageTag;

    private final String regionCode;

    private final JsonNode channels;

    private final JsonNode definitions;

    private final long languageVersion;

    private final long regionVersion;

    private final long channelsVersion;

    private NotificationSettingsView(
            NotificationSettingsSnapshot snapshot,
            Collection<NotificationType<?>> notificationTypes) {
        this.languageTag = snapshot.language().toLanguageTag();
        this.regionCode = snapshot.region().code();
        this.channels = snapshot.channels();
        this.definitions = definitions(notificationTypes);
        this.languageVersion = snapshot.languageVersion().value();
        this.regionVersion = snapshot.regionVersion().value();
        this.channelsVersion = snapshot.channelsVersion().value();
    }

    /** @return 通知设置聚合 HTTP 投影 */
    public static @NotNull NotificationSettingsView from(
            @NotNull NotificationSettingsSnapshot snapshot,
            @NotNull Collection<NotificationType<?>> notificationTypes) {
        return new NotificationSettingsView(
                Objects.requireNonNull(snapshot, "通知设置不能为空"),
                Objects.requireNonNull(notificationTypes, "通知类型不能为空"));
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public JsonNode getChannels() {
        return channels.deepCopy();
    }

    /** @return 通知类型到必发或可选渠道声明的只读领域投影 */
    public JsonNode getDefinitions() {
        return definitions.deepCopy();
    }

    public long getLanguageVersion() {
        return languageVersion;
    }

    public long getRegionVersion() {
        return regionVersion;
    }

    public long getChannelsVersion() {
        return channelsVersion;
    }

    private static JsonNode definitions(Collection<NotificationType<?>> notificationTypes) {
        var result = JsonNodeFactory.instance.objectNode();
        notificationTypes.stream()
                .sorted(Comparator.comparing(type -> type.code().format(SLASH)))
                .forEach(type -> {
                    var channels = result.putObject(type.code().format(SLASH));
                    type.definition().requiredChannels().forEach(channel -> {
                        var value = channels.putObject(channel.format(SLASH));
                        value.put("required", true);
                    });
                    type.definition().optionalChannels().forEach((channel, defaultEnabled) -> {
                        var value = channels.putObject(channel.format(SLASH));
                        value.put("required", false);
                        value.put("defaultEnabled", defaultEnabled);
                    });
                });
        return result;
    }
}
