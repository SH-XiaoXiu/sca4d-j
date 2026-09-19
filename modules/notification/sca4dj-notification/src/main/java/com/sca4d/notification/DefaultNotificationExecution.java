package com.sca4d.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationChannel;
import com.sca4d.core.api.notification.NotificationDefinition;
import com.sca4d.core.api.notification.NotificationExecution;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.core.api.notification.NotificationType;
import com.sca4d.core.api.region.Region;
import com.sca4d.core.api.setting.SettingOwner;
import com.sca4d.core.api.setting.Settings;
import com.sca4d.notification.config.NotificationSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 执行一个通知类型的接收人回调，并把 Settings、语言和区域解析成标准通知。 */
final class DefaultNotificationExecution<T> implements NotificationExecution {

    private final @NotNull NotificationType<T> type;

    private final @NotNull T value;

    private final @NotNull Settings settings;

    private final @NotNull NotificationLocaleResolver locales;

    private final @NotNull NotificationRegionResolver regions;

    private final @NotNull NotificationDeliveryRouter deliveries;

    private @Nullable Locale explicitLocale;

    private @Nullable Region explicitRegion;

    private @Nullable Notification result;

    /** 创建一次只能确定一个接收人的通知执行。 */
    DefaultNotificationExecution(
            @NotNull NotificationType<T> type,
            @NotNull T value,
            @NotNull Settings settings,
            @NotNull NotificationLocaleResolver locales,
            @NotNull NotificationRegionResolver regions,
            @NotNull NotificationDeliveryRouter deliveries) {
        this.type = Objects.requireNonNull(type, "通知类型不能为空");
        this.value = Objects.requireNonNull(value, "领域通知不能为空");
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        this.locales = Objects.requireNonNull(locales, "通知语言解析器不能为空");
        this.regions = Objects.requireNonNull(regions, "通知区域解析器不能为空");
        this.deliveries = Objects.requireNonNull(deliveries, "通知渠道路由器不能为空");
    }

    @Override
    public @NotNull NotificationExecution locale(@NotNull Locale locale) {
        requireUnresolved();
        Locale checked = Locale.forLanguageTag(Objects.requireNonNull(locale, "通知 Locale 不能为空").toLanguageTag());
        if (checked.getLanguage().isBlank()) {
            throw new IllegalArgumentException("通知 Locale 必须包含语言");
        }
        explicitLocale = checked;
        return this;
    }

    @Override
    public @NotNull NotificationExecution region(@NotNull Region region) {
        requireUnresolved();
        explicitRegion = Objects.requireNonNull(region, "通知区域不能为空");
        return this;
    }

    @Override
    public void sendToAccount(long accountId) {
        sendTo(NotificationRecipient.account(accountId));
    }

    @Override
    public void sendTo(@NotNull NotificationRecipient recipient) {
        requireUnresolved();
        NotificationRecipient checkedRecipient = Objects.requireNonNull(recipient, "通知接收人不能为空");

        // 按显式值或具体应用解析器确定接收人的最终 Locale、业务区域和模板变量。
        Locale locale = explicitLocale == null ? locales.resolve(checkedRecipient) : explicitLocale;
        Region region = explicitRegion == null
                ? regions.resolve(checkedRecipient).orElse(null)
                : explicitRegion;
        Map<String, String> variables = variables(locale);

        // 框架内部读取系统默认值和接收人覆盖值，冻结本次通知真正需要的渠道。
        JsonNode systemDefaults = settings.get(NotificationSettings.CHANNEL_DEFAULTS);
        JsonNode preferences = settings.get(
                NotificationSettings.CHANNEL_PREFERENCES,
                new SettingOwner(checkedRecipient.type().value(), checkedRecipient.id()));
        Set<NotificationChannel> intended = intendedChannels(systemDefaults, preferences);
        if (intended.isEmpty()) {
            throw new IllegalStateException("通知没有选中任何渠道: " + type.code());
        }
        Notification candidate = new Notification(
                type.code(),
                checkedRecipient,
                locale,
                variables,
                region,
                intended);

        // 必发渠道不可用时拒绝发布；当前不可用的可选渠道不进入最终消息。
        Set<NotificationChannel> available = availableChannels(candidate, intended);
        if (available.isEmpty()) {
            throw new IllegalStateException("通知没有可用渠道: " + type.code());
        }
        result = new Notification(type.code(), checkedRecipient, locale, variables, region, available);
    }

    /** @return 回调完成后产生的唯一标准通知 */
    @NotNull
    Notification result() {
        return Objects.requireNonNull(result, "通知类型回调没有指定接收人");
    }

    /** 按最终 Locale 从领域对象提取模板变量。 */
    private @NotNull Map<String, String> variables(@NotNull Locale locale) {
        Map<String, String> resolved = new LinkedHashMap<>();
        type.definition()
                .variables()
                .forEach(
                        (name, resolver) -> resolved.put(
                                name,
                                Objects.requireNonNull(resolver.apply(value, locale), "通知变量回调不能返回 null: " + name)));
        return Map.copyOf(resolved);
    }

    /** 按代码硬约束、接收人覆盖、系统覆盖和 Java 初值确定目标渠道。 */
    private @NotNull Set<NotificationChannel> intendedChannels(
            @NotNull JsonNode systemDefaults,
            @NotNull JsonNode preferences) {
        NotificationDefinition<T> definition = type.definition();
        Set<NotificationChannel> selected = new LinkedHashSet<>(definition.requiredChannels());
        definition.optionalChannels().forEach((channel, defaultEnabled) -> {
            if (preference(preferences, channel)
                    .or(() -> preference(systemDefaults, channel))
                    .orElse(defaultEnabled)) {
                selected.add(channel);
            }
        });
        return Set.copyOf(selected);
    }

    /** 要求必发渠道可用，并忽略当前不可用的可选渠道。 */
    private @NotNull Set<NotificationChannel> availableChannels(
            @NotNull Notification candidate,
            @NotNull Set<NotificationChannel> intended) {
        Set<NotificationChannel> available = new LinkedHashSet<>();
        for (NotificationChannel channel : intended) {
            if (deliveries.available(channel, candidate)) {
                available.add(channel);
            } else if (type.definition().requiredChannels().contains(channel)) {
                throw new IllegalStateException("通知必发渠道不可用: " + type.code() + '/' + channel);
            }
        }
        return Set.copyOf(available);
    }

    /** 读取当前通知类型下一个渠道的显式布尔覆盖。 */
    private @NotNull Optional<Boolean> preference(
            @NotNull JsonNode root,
            @NotNull NotificationChannel channel) {
        JsonNode valueNode = Objects.requireNonNull(root, "通知渠道设置不能为空")
                .path(type.code().value())
                .path(channel.value());
        return valueNode.isBoolean() ? Optional.of(valueNode.booleanValue()) : Optional.empty();
    }

    /** 回调确定接收人后不允许继续改写 Locale、区域或接收人。 */
    private void requireUnresolved() {
        if (result != null) {
            throw new IllegalStateException("一次领域通知只能确定一个接收人");
        }
    }
}
