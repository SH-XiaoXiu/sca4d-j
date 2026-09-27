package com.sca4d.app.server.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.region.Region;
import com.sca4d.core.api.setting.SettingOwner;
import com.sca4d.core.api.setting.Settings;
import com.sca4d.notification.NotificationTypeCatalog;
import com.sca4d.notification.config.NotificationSettings;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

/** 用领域接口包装 Notification 的 Account 语言、区域和可选渠道 Settings。 */
@Service
public class NotificationSettingsService {
    private final Settings settings;

    private final NotificationTypeCatalog types;

    /** 创建用户通知设置服务。 */
    public NotificationSettingsService(@NotNull Settings settings, @NotNull NotificationTypeCatalog types) {
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        this.types = Objects.requireNonNull(types, "NotificationTypeCatalog 不能为空");
    }

    /** @return 当前 Account 的语言、区域和渠道覆盖值 */
    @Transactional(readOnly = true)
    public @NotNull NotificationSettingsSnapshot inspect(long accountId) {
        SettingOwner owner = owner(accountId);
        var language = settings.inspect(PlatformNotificationConfiguration.LANGUAGE, owner);
        var region = settings.inspect(PlatformNotificationConfiguration.REGION, owner);
        var channels = settings.inspect(NotificationSettings.CHANNEL_PREFERENCES, owner);
        return new NotificationSettingsSnapshot(
                Locale.forLanguageTag(language.value()),
                new Region(region.value()),
                channels.value(),
                language.version(),
                region.version(),
                channels.version());
    }

    /** 完整替换当前 Account 的 BCP 47 全局语言。 */
    @Transactional
    public void replaceLanguage(long accountId, @NotNull String languageTag) {
        Locale locale = Locale.forLanguageTag(Objects.requireNonNull(languageTag, "语言标签不能为空"));
        if (locale.getLanguage().isBlank())
            throw new IllegalArgumentException("语言标签必须包含语言");
        settings.replace(PlatformNotificationConfiguration.LANGUAGE, owner(accountId), locale.toLanguageTag());
    }

    /** 完整替换当前 Account 的业务区域。 */
    @Transactional
    public void replaceRegion(long accountId, @NotNull String regionCode) {
        settings.replace(
                PlatformNotificationConfiguration.REGION,
                owner(accountId),
                new Region(regionCode).code());
    }

    /** 完整替换当前 Account 对可选通知渠道的覆盖值。 */
    @Transactional
    public void replaceChannels(long accountId, @NotNull JsonNode preferences) {
        JsonNode checked = Objects.requireNonNull(preferences, "通知渠道偏好不能为空");
        if (!checked.isObject())
            throw new IllegalArgumentException("通知渠道偏好必须是 JSON object");
        checked.properties().forEach(typeEntry -> {
            TreeCode typeCode = TreeCode.of(typeEntry.getKey());
            var type = types.types()
                    .stream()
                    .filter(candidate -> candidate.code().equals(typeCode))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("未知通知类型: " + typeEntry.getKey()));
            if (!typeEntry.getValue().isObject())
                throw new IllegalArgumentException("通知类型偏好必须是 object");
            typeEntry.getValue().properties().forEach(channelEntry -> {
                TreeCode channel = TreeCode.ofSegment(channelEntry.getKey());
                if (!channelEntry.getValue().isBoolean()
                        || !type.definition().optionalChannels().containsKey(channel)) {
                    throw new IllegalArgumentException("只有可选渠道可以保存布尔偏好: " + typeEntry.getKey() + '/' + channel);
                }
            });
        });
        settings.replace(NotificationSettings.CHANNEL_PREFERENCES, owner(accountId), checked.deepCopy());
    }

    private static SettingOwner owner(long accountId) {
        if (accountId <= 0)
            throw new IllegalArgumentException("Account 标识必须为正数");
        return new SettingOwner(Account.RESOURCE_TYPE, Long.toString(accountId));
    }
}
