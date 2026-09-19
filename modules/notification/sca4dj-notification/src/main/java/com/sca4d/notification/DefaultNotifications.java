package com.sca4d.notification;

import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.messaging.MessagePublisher;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationChannel;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.core.api.notification.NotificationType;
import com.sca4d.core.api.notification.Notifications;
import com.sca4d.core.api.setting.Settings;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 使用通知类型回调解析领域对象，并执行同步或 Outbox 发布。 */
public final class DefaultNotifications implements Notifications {

    private final @NotNull NotificationTypeCatalog types;

    private final @NotNull Settings settings;

    private final @NotNull NotificationLocaleResolver locales;

    private final @NotNull NotificationRegionResolver regions;

    private final @NotNull NotificationDeliveryRouter deliveries;

    private final @NotNull MessagePublisher messages;

    private final @NotNull IdGenerator ids;

    private final @NotNull Clock clock;

    /** 创建类型化通知发布入口。 */
    public DefaultNotifications(
            @NotNull NotificationTypeCatalog types,
            @NotNull Settings settings,
            @NotNull NotificationLocaleResolver locales,
            @NotNull NotificationRegionResolver regions,
            @NotNull NotificationDeliveryRouter deliveries,
            @NotNull MessagePublisher messages,
            @NotNull IdGenerator ids,
            @NotNull Clock clock) {
        this.types = Objects.requireNonNull(types, "通知类型目录不能为空");
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        this.locales = Objects.requireNonNull(locales, "通知语言解析器不能为空");
        this.regions = Objects.requireNonNull(regions, "通知区域解析器不能为空");
        this.deliveries = Objects.requireNonNull(deliveries, "通知渠道路由器不能为空");
        this.messages = Objects.requireNonNull(messages, "MessagePublisher 不能为空");
        this.ids = Objects.requireNonNull(ids, "IdGenerator 不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");
    }

    @Override
    public boolean isAvailable(@NotNull NotificationType<?> type, @NotNull NotificationRecipient recipient) {
        NotificationType<?> checkedType = types.require(type);
        NotificationRecipient checkedRecipient = Objects.requireNonNull(recipient, "通知接收人不能为空");
        Set<NotificationChannel> required = checkedType.definition().requiredChannels();
        if (required.isEmpty()) {
            return true;
        }
        Notification candidate = new Notification(
                checkedType.code(),
                checkedRecipient,
                locales.resolve(checkedRecipient),
                Map.of(),
                regions.resolve(checkedRecipient).orElse(null),
                required);
        return required.stream().allMatch(channel -> deliveries.available(channel, candidate));
    }

    @Override
    public <T> void sendNow(@NotNull NotificationType<T> type, @NotNull T value) {
        Notification notification = resolve(type, value);
        deliveries.deliver(new MessageId(ids.nextId()), notification);
    }

    @Override
    public <T> @NotNull MessageId enqueue(@NotNull NotificationType<T> type, @NotNull T value) {
        Notification notification = resolve(type, value);
        Event<NotificationRequested> event = new Event<>(
                NotificationEvents.REQUESTED,
                null,
                clock.instant(),
                NotificationRequested.from(notification));
        return messages.publishReliably(event);
    }

    /** 执行类型回调并取得一个已经冻结路由事实的标准通知。 */
    private <T> @NotNull Notification resolve(@NotNull NotificationType<T> type, @NotNull T value) {
        NotificationType<T> checkedType = types.require(type);
        DefaultNotificationExecution<T> execution = new DefaultNotificationExecution<>(
                checkedType,
                Objects.requireNonNull(value, "领域通知不能为空"),
                settings,
                locales,
                regions,
                deliveries);
        checkedType.execute(value, execution);
        return execution.result();
    }
}
