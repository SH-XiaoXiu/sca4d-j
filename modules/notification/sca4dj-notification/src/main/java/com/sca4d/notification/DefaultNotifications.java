package com.sca4d.notification;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.core.api.notification.NotificationType;
import com.sca4d.core.api.notification.Notifications;
import com.sca4d.notification.config.NotificationSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 使用通知类型注册的回调从业务对象生成通知，并同步发布或写入 Outbox。
 */
public class DefaultNotifications implements Notifications {

    private final @NotNull IdGenerator ids;

    private final @NotNull MessageBus messages;

    private final @NotNull NotificationDeliveryRouter deliveries;

    private final @NotNull NotificationLocaleResolver locales;

    private final @NotNull NotificationRegionResolver regions;

    private final @NotNull NotificationSettings settings;

    private final @NotNull NotificationTypeCatalog types;

    /**
     * 创建类型化通知发布入口。
     */
    public DefaultNotifications(
            @NotNull NotificationTypeCatalog types,
            @NotNull NotificationSettings settings,
            @NotNull NotificationLocaleResolver locales,
            @NotNull NotificationRegionResolver regions,
            @NotNull NotificationDeliveryRouter deliveries,
            @NotNull MessageBus messages,
            @NotNull IdGenerator ids) {
        this.types = Objects.requireNonNull(types, "通知类型目录不能为空");
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        this.locales = Objects.requireNonNull(locales, "通知语言解析器不能为空");
        this.regions = Objects.requireNonNull(regions, "通知区域解析器不能为空");
        this.deliveries = Objects.requireNonNull(deliveries, "通知渠道路由器不能为空");
        this.messages = Objects.requireNonNull(messages, "MessageBus 不能为空");
        this.ids = Objects.requireNonNull(ids, "IdGenerator 不能为空");
    }

    @Override
    public boolean isAvailable(@NotNull NotificationType<?> type, @NotNull NotificationRecipient recipient) {
        NotificationType<?> checkedType = types.require(type);
        NotificationRecipient checkedRecipient = Objects.requireNonNull(recipient, "通知接收人不能为空");
        Set<TreeCode> required = checkedType.definition().requiredChannels();
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
        Notification notification = resolve(type, value, false);
        deliveries.deliver(ids.nextId(), notification);
    }

    @Override
    public <T> @NotNull Id enqueue(@NotNull NotificationType<T> type, @NotNull T value) {
        Notification notification = resolve(type, value, true);
        return messages.dispatch(new NotificationMessage(notification), DispatchPolicy.outbox()).messageId();
    }

    /**
     * 执行类型回调，返回渠道路由已经确定的标准 Notification。
     */
    private <T> @NotNull Notification resolve(
            @NotNull NotificationType<T> type,
            @NotNull T value,
            boolean deferAvailability) {
        NotificationType<T> checkedType = types.require(type);
        DefaultNotificationExecution<T> execution = new DefaultNotificationExecution<>(
                checkedType,
                Objects.requireNonNull(value, "领域通知不能为空"),
                settings,
                locales,
                regions,
                deliveries,
                deferAvailability);
        checkedType.execute(value, execution);
        return execution.result();
    }
}
